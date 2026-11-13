package com.moli.agent.scope.service;

import com.moli.agent.scope.common.base.BizException;
import com.moli.agent.scope.common.vo.ConfirmDecision;
import com.moli.agent.scope.common.vo.ConfirmRequest;
import io.agentscope.core.event.ConfirmResult;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.ToolCallState;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.state.AgentState;
import io.agentscope.core.state.VersionedState;
import io.agentscope.extensions.mysql.state.MysqlAgentStateStore;
import io.agentscope.harness.agent.HarnessAgent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 会话上下文（AgentState）的写侧操作。
 * <p>{@link MessageHistoryService} 负责「读」——把 agentscope_sessions 里的上下文渲染成历史消息；
 * 这里负责「改」——目前只有重新生成前的轮次回滚（按用户消息 ID 定位回滚点）。
 * <p>AgentScope 没有提供 rollback / regenerate 语义的 API（{@code clearContext} 是整会话清空，
 * 相当于新开会话，对重新生成过于粗暴），因此只能按下面的顺序手工截断。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationContextService {

    /**
     * AgentScope 持久化对话上下文所用的 stateKey（SDK 内部约定值，
     * {@link MessageHistoryService} 读取时用的是同一个键）。
     */
    public static final String STATE_KEY_AGENT = "agent_state";

    private final MysqlAgentStateStore agentStateStore;

    private final HarnessAgent harnessAgent;

    /**
     * 重新生成前把上下文回滚到目标轮次之前：按用户消息 ID 定位该轮，
     * 删除它及其后的全部消息（本轮产生的 ASSISTANT / TOOL 一并回滚），
     * 随后本次请求会把这条用户消息重新追加进上下文。
     * <p>不回滚就会出现「同一条用户消息在上下文里躺两遍、旧答案也还在」的情况，
     * 模型据此产生「你这句话发了几次」这类元评论，且旧答案会让重新生成越来越不像重新生成。
     * <p>定位完全以 ID 为准（ID 由 {@code SimpleHarnessActAgent} 预生成并随 DONE 事件回传前端）：
     * 上下文里可以出现多条文本完全相同的用户消息，只有 ID 能唯一定位，按文本找会找错。
     * <p>定位不到直接抛 {@link BizException}，不做任何退回猜测（按文本匹配、按最后一轮回滚等兜底
     * 都会静默选错回滚点，把上下文改坏比失败一次更难排查）；message 是给用户看的，
     * sessionId / 消息 ID 等排查细节留在日志里。
     * <p>因为所有校验与定位都排在内存修改与回写之前，抛异常时数据库一个字节都没改，
     * 不会留下「回滚了一半」的中间状态；此时本轮对话尚未进入 Agent，框架的
     * {@code agentscope_sessions} 也不会多出只有 user 没有 assistant 的半截轮次。
     * <p>被回滚的旧回答若在 m_chat_message_file 留有产物记录，这里不删除：
     * 记录保留便于审计与找回，代价是历史加载时那条已不存在的答案不会再挂上附件。
     * <p>并发前提：前端重发前已 abort 旧流并调用过 /agent/conversation/stop，
     * 因此正常不会与上一轮的收尾写入相撞。若旧轮次仍在本次截断之后才落库，
     * 它的写入会覆盖回滚结果（重复消息重现），此时靠下一次重新生成的回滚收敛；
     * 要彻底消除需做会话级串行化（同一 sessionId 的写入排队），当前未引入。
     *
     * @param userId              用户ID（与 AgentStateStore 的 (userId, sessionId) 槽位一致）
     * @param sessionId           会话ID
     * @param targetUserMessageId 要重放的那条用户消息 ID，必填
     * @param incomingMessage     本次重新生成的用户消息，仅用于回滚点文本比对留痕
     * @return 被删除的消息条数
     * @throws BizException 请求缺 sessionId / 缺目标消息 ID，或会话状态与目标消息对不上
     */
    public int truncateForRegenerate(String userId, String sessionId, String targetUserMessageId,
                                    String incomingMessage) {
        if (StringUtils.isBlank(sessionId)) {
            log.warn("重新生成失败：请求缺少会话 ID, userId={}", userId);
            throw new BizException("重新生成失败：缺少会话标识，请刷新页面后重试");
        }
        if (StringUtils.isBlank(targetUserMessageId)) {
            log.warn("重新生成失败：请求缺少要重放的用户消息 ID, userId={}, sessionId={}", userId, sessionId);
            throw new BizException("重新生成失败：缺少要重放的消息标识，请刷新页面后重试");
        }

        // 带版本读取：回写时用乐观锁，避免与仍在收尾的上一轮互相覆盖
        VersionedState<AgentState> versioned = agentStateStore.getVersioned(
                userId, sessionId, STATE_KEY_AGENT, AgentState.class);
        if (!versioned.isPresent() || versioned.value() == null) {
            log.warn("重新生成失败：会话没有已持久化的上下文, userId={}, sessionId={}, targetMessageId={}",
                    userId, sessionId, targetUserMessageId);
            throw new BizException("重新生成失败：该会话还没有可回滚的历史记录，请刷新页面后重试");
        }

        AgentState state = versioned.value();
        // contextMutable 才是可写视图；getContext 返回的是只读快照，改了不会落库
        List<Msg> context = state.contextMutable();
        int rollbackIndex = indexOfUserMessageById(context, targetUserMessageId);
        if (rollbackIndex < 0) {
            // 可能是上下文已被压缩/淘汰把目标消息移出窗口，也可能是前端传了过期 ID
            log.warn("重新生成失败：上下文里找不到目标用户消息, userId={}, sessionId={}, targetMessageId={}, 当前上下文 {} 条",
                    userId, sessionId, targetUserMessageId, context.size());
            throw new BizException("重新生成失败：这条消息已不在会话上下文中（可能被历史压缩或会话已更新），请刷新页面后重试");
        }

        // ID 命中即为权威回滚点；文本不同只说明用户改过重放内容，留痕即可
        String contextText = StringUtils.trimToEmpty(context.get(rollbackIndex).getTextContent());
        String incomingText = StringUtils.trimToEmpty(incomingMessage);
        if (!StringUtils.equals(contextText, incomingText)) {
            log.warn("重新生成：回滚点文本与本次请求不一致。sessionId={}, 回滚点=\"{}\", 本次=\"{}\"",
                    sessionId, StringUtils.abbreviate(contextText, 80), StringUtils.abbreviate(incomingText, 80));
        }

        int removed = context.size() - rollbackIndex;
        while (context.size() > rollbackIndex) {
            context.remove(context.size() - 1);
        }
        // 已退回轮次边界，本轮的 ReAct 迭代计数也要归零，否则新回答可能一开局就撞上 maxIters
        state.setCurIter(0);

        // saveIfVersion 成功返回新版本号，版本不匹配（期间有并发写入）返回 -1
        long newVersion = agentStateStore.saveIfVersion(
                userId, sessionId, STATE_KEY_AGENT, state, versioned.version());
        if (newVersion < 0) {
            log.warn("重新生成：状态版本冲突（expectedVersion={}），改为直接覆盖回写: sessionId={}",
                    versioned.version(), sessionId);
            agentStateStore.save(userId, sessionId, STATE_KEY_AGENT, state);
        }

        // 关键：Agent 进程内仍缓存着回滚前的 state，本轮结束时它会刷回存储并吞掉这次截断，
        // 所以必须清缓存，让紧随其后的 streamEvents 重新从库里加载
        harnessAgent.clearStateCache(userId, sessionId);

        log.info("重新生成：已回滚上下文, userId={}, sessionId={}, 回滚点下标={}, 删除 {} 条消息, 剩余 {} 条",
                userId, sessionId, rollbackIndex, removed, context.size());
        return removed;
    }

    /**
     * 查出会话当前「等待用户确认」的工具调用（人在回路待确认项）。
     * <p>与 SDK 内部 {@code ReActAgent.CallExecution#askingToolCalls()} 保持同一口径：
     * 只看最后一条 ASSISTANT 消息，命中 ASKING 状态的 {@code ToolUseBlock} 才算待确认。
     * 这个口径必须对齐，否则这里报“有待确认”而框架报“没有”（或反之），
     * 两边的判断会互相打脸。
     * <p>ASKING 状态随 AgentState 一同落库（{@code ToolUseBlock} 的 state 字段参与 JSON 序列化），
     * 因此刷新页面、重启服务后重新进同一会话，确认卡片依旧能恢复。
     * <p>只读不写，不依赖 Agent 的本地状态缓存（缓存实例与本次 HTTP 请求未必同一 JVM），
     * 直接读持久化存储——那才是下一次 call 开始时真正被重新加载的权威源。
     *
     * @param userId    用户ID
     * @param sessionId 会话ID
     * @return 待确认的工具调用；无会话状态、无 ASKING 调用时返回空列表
     */
    public List<ToolUseBlock> askingToolCalls(String userId, String sessionId) {
        if (StringUtils.isBlank(sessionId)) {
            return List.of();
        }
        VersionedState<AgentState> versioned = agentStateStore.getVersioned(
                userId, sessionId, STATE_KEY_AGENT, AgentState.class);
        if (!versioned.isPresent() || versioned.value() == null) {
            return List.of();
        }
        List<Msg> context = versioned.value().getContext();
        for (int i = context.size() - 1; i >= 0; i--) {
            Msg msg = context.get(i);
            if (msg == null || msg.getRole() != MsgRole.ASSISTANT) {
                continue;
            }
            // 只看最后一条 assistant 消息就返回（可能是空列表），与 SDK 口径一致
            return msg.getContentBlocks(ToolUseBlock.class).stream()
                    .filter(toolUse -> toolUse.getState() == ToolCallState.ASKING)
                    .toList();
        }
        return List.of();
    }

    /**
     * 会话上下文里最后一条用户提问的原文。
     * <p>人在回路恢复那次调用只带裁决 metadata、不带文本（框架也不会把它追加进上下文），
     * 因此产物中间件那一轮拿不到提问原文，写出的产物会缺 [用户] 那一行，这里按上下文补回来。
     * <p>只读不写。多一次状态读取可以接受：确认恢复是用户点一次才发生一次的低频路径。
     *
     * @param userId    用户ID
     * @param sessionId 会话ID
     * @return 提问原文；无会话状态或上下文里没有带文本的用户消息时返回空串
     */
    public String lastUserMessageText(String userId, String sessionId) {
        if (StringUtils.isBlank(sessionId)) {
            return "";
        }
        VersionedState<AgentState> versioned = agentStateStore.getVersioned(
                userId, sessionId, STATE_KEY_AGENT, AgentState.class);
        if (!versioned.isPresent() || versioned.value() == null) {
            return "";
        }
        List<Msg> context = versioned.value().getContext();
        for (int i = context.size() - 1; i >= 0; i--) {
            Msg msg = context.get(i);
            if (msg == null || msg.getRole() != MsgRole.USER) {
                continue;
            }
            // 确认恢复时框架会追加一条只带 metadata 的 USER 消息，文本为空则继续往前找
            String text = StringUtils.trimToEmpty(msg.getTextContent());
            if (!text.isEmpty()) {
                return text;
            }
        }
        return "";
    }

    /**
     * 把前端的确认裁决加回成 SDK 的 {@code ConfirmResult}（人在回路恢复的组装环节）。
     * <p>入参以会话上下文里那条 ASKING 的 {@code ToolUseBlock} 原始值为准，只从前端拿
     * 「哪个调用、允许还是拒绝、拒绝原因」三件事。若直接拿前端回传的 toolCall 去恢复，
     * 确认卡片就成了改命令的入口（前端改一下 path / command，被确认执行的却是另一个）。
     * <p>校验都在返回之前完成，对不上直接抛 {@link BizException}，不会把错误的 metadata 下发给框架：
     * 框架的 {@code validateAndAcceptConfirmResults} 同样会拒，但它报的是面向开发者的长篇 IllegalState。
     *
     * @param userId    用户ID
     * @param sessionId 会话ID
     * @param decisions 前端的裁决列表
     * @return 与 ASKING 调用一一对应的 ConfirmResult，非空
     * @throws BizException 没有等待确认的操作，或裁决 ID 为空/重复/对不上
     */
    public List<ConfirmResult> buildConfirmResults(String userId, String sessionId,
                                                   List<ConfirmDecision> decisions) {
        List<ToolUseBlock> asking = askingToolCalls(userId, sessionId);
        if (asking.isEmpty()) {
            log.warn("确认恢复失败：会话没有等待确认的工具调用, userId={}, sessionId={}", userId, sessionId);
            throw new BizException("没有等待确认的操作，可能已经处理过了，请重新发送你的问题");
        }
        Map<String, ToolUseBlock> askingById = new LinkedHashMap<>();
        for (ToolUseBlock toolUse : asking) {
            if (StringUtils.isNotBlank(toolUse.getId())) {
                askingById.put(toolUse.getId(), toolUse);
            }
        }

        List<ConfirmResult> results = new ArrayList<>();
        for (ConfirmDecision decision : decisions) {
            if (decision == null || StringUtils.isBlank(decision.getToolCallId())) {
                throw new BizException("确认请求缺少工具调用标识，请在确认卡片上重新操作一次");
            }
            ToolUseBlock toolCall = askingById.remove(decision.getToolCallId());
            if (toolCall == null) {
                log.warn("确认恢复失败：裁决的调用 ID 不在待确认列表里（重复提交或会话已更新）, sessionId={}, id={}",
                        sessionId, decision.getToolCallId());
                throw new BizException("这个确认已经失效了（会话已更新或重复提交），请刷新页面后重试");
            }
            results.add(new ConfirmResult(decision.isConfirmed(), toolCall, null, decision.getReason()));
        }
        return results;
    }

    /**
     * 会话当前待确认项的前端视图（用于非流式场景恢复确认卡片）。
     * <p>刷新页面、切换会话、重启服务后重新打开同一个会话时，流里的 CONFIRM 事件早已飞走，
     * 但上下文里的 ASKING 状态依旧在——此时必须从历史接口把卡片补回来，
     * 否则用户既看不到待确认入口，又因为会话卡在 ASKING 而发不进任何新消息。
     *
     * @param userId    用户ID
     * @param sessionId 会话ID
     * @return 待确认列表；无待确认项时为空列表
     */
    public List<ConfirmRequest> askingConfirmRequests(String userId, String sessionId) {
        return toConfirmRequests(askingToolCalls(userId, sessionId));
    }

    /**
     * 把 SDK 的待确认工具调用转成前端的确认卡片数据（事件下发与历史恢复两条路径共用）。
     * <p>{@code toolCallId} 是恢复会话的唯一凭据，必须原样下发、原样回收。
     * <p>这里只下发“要做什么”的展示信息，工具入参的具体值不会被当成恢复依据（见
     * {@link #buildConfirmResults}），所以确认卡片不是改命令的入口。
     */
    public List<ConfirmRequest> toConfirmRequests(List<ToolUseBlock> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return List.of();
        }
        return toolCalls.stream().map(ConversationContextService::toConfirmRequest).toList();
    }

    private static ConfirmRequest toConfirmRequest(ToolUseBlock toolUse) {
        // 反射构造的工具调用有可能没带 input，兼容为空的情况，不让确认卡片在渲染时抛 NPE
        Map<String, Object> input = toolUse.getInput() == null ? Map.of() : toolUse.getInput();
        return ConfirmRequest.builder()
                .toolCallId(toolUse.getId())
                .toolName(toolUse.getName())
                .arguments(input)
                .argumentSummary(summarizeToolInput(input))
                .build();
    }

    /**
     * 确认卡片上的一行摘要：优先取最能说明“要动什么”的参数（命令原文 / 目录路径），
     * 其余工具退化为入参文本，省掉前端为每个工具写一套取值判断
     */
    private static String summarizeToolInput(Map<String, Object> input) {
        Object summary = input.get("command");
        if (summary == null) {
            summary = input.get("path");
        }
        if (summary != null) {
            return StringUtils.abbreviate(String.valueOf(summary), 300);
        }
        return input.isEmpty() ? "（无参数）" : StringUtils.abbreviate(input.toString(), 300);
    }

    /** 按消息 ID 正序查找目标用户消息的下标；ID 为空或找不到返回 -1 */
    private int indexOfUserMessageById(List<Msg> context, String messageId) {
        if (StringUtils.isBlank(messageId)) {
            return -1;
        }
        for (int i = 0; i < context.size(); i++) {
            Msg msg = context.get(i);
            // 只认 USER：ID 本身是全局唯一的 UUID，角色校验能挡住前端传错（比如误传回答消息的 id）
            if (msg != null && msg.getRole() == MsgRole.USER && StringUtils.equals(messageId, msg.getId())) {
                return i;
            }
        }
        return -1;
    }
}
