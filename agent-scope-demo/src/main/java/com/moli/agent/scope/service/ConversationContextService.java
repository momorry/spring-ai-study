package com.moli.agent.scope.service;

import com.moli.agent.scope.common.base.BizException;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.state.AgentState;
import io.agentscope.core.state.VersionedState;
import io.agentscope.extensions.mysql.state.MysqlAgentStateStore;
import io.agentscope.harness.agent.HarnessAgent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;

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
