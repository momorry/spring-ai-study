package com.moli.agent.scope.service;

import com.moli.agent.scope.common.dao.entity.MChatArtifact;
import com.moli.agent.scope.common.dao.manager.MChatArtifactManager;
import com.moli.agent.scope.common.vo.ConfirmRequest;
import com.moli.agent.scope.common.vo.FileInfo;
import com.moli.agent.scope.common.vo.HistoryMessageVo;
import com.moli.agent.scope.common.vo.ToolCallVo;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ThinkingBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.state.AgentState;
import io.agentscope.extensions.mysql.state.MysqlAgentStateStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageHistoryService {


    /**
     * MySQL 会话状态存储（agentscope-extensions-mysql）。
     * Agent 已绑定该 store，会按 RuntimeContext 的 userId/sessionId 自动持久化 / 恢复对话状态；
     * 这里额外用它探测会话恢复情况。
     */
    private final MysqlAgentStateStore agentStateStore;

    /**
     * 对话产物台账（m_chat_artifact）。
     * <p>加载历史消息时按 messageId 批量查出本轮交付的产物，挂到对应回答上；
     * 与流式下发那一份卡片走的是同一个转换（{@link FileInfo#from}），刷新前后才是同一个东西。
     */
    private final MChatArtifactManager artifactManager;

    /**
     * 会话上下文读侧（只读部分）：从 ASKING 工具调用恢复人在回路的待确认卡片。
     */
    private final ConversationContextService conversationContextService;

    /**
     * 从 AgentScope 持久化存储中加载会话历史消息。
     *
     * @param userId    用户ID
     * @param sessionId 会话ID
     * @return 历史消息列表
     */
    public List<HistoryMessageVo> loadHistory(String userId, String sessionId) {
        log.info("加载会话历史: userId={}, sessionId={}", userId, sessionId);

        List<HistoryMessageVo> result = new ArrayList<>();
        // 产物是按 message_id 关联的，所以每条 assistant 消息都要登记「自己属于哪个气泡 + 自己的 messageId」；
        // 一轮里的多条 assistant 消息会被合并成同一个气泡，靠这两个平行数组才能把产物挂回正确的气泡
        List<String> assistantMessageIds = new ArrayList<>();
        List<Integer> assistantIndices = new ArrayList<>();

        try {
            // 从 AgentStateStore 加载 AgentState（stateKey 与写侧 ConversationContextService 共用同一常量）
            Optional<AgentState> stateOpt = agentStateStore.get(
                    userId, sessionId, ConversationContextService.STATE_KEY_AGENT, AgentState.class);

            if (stateOpt.isEmpty()) {
                log.info("未找到会话历史: userId={}, sessionId={}", userId, sessionId);
                return result;
            }

            AgentState state = stateOpt.get();
            List<Msg> context = state.getContext();

            if (context == null || context.isEmpty()) {
                log.info("会话历史为空: userId={}, sessionId={}", userId, sessionId);
                return result;
            }

            // 本轮（同一条用户消息之后）已经合并到的 assistant 气泡；遇到用户消息就重新开一轮
            HistoryMessageVo turnBubble = null;

            for (Msg msg : context) {
                // 跳过 system 消息（系统提示词）
                if (msg.getRole() == null || "SYSTEM".equals(msg.getRole().name())) {
                    continue;
                }

                // 工具结果不单独成一条历史消息：它只含 ToolResultBlock，下面的正文提取读不到任何内容，
                // 前端会渲染出一个空白的「青梧」气泡，还会把「重新生成」用 index-1 找上一条用户消息的逻辑带偏
                if ("TOOL".equals(msg.getRole().name())) {
                    foldToolResults(turnBubble, msg);
                    continue;
                }

                boolean assistant = "ASSISTANT".equals(msg.getRole().name());
                // 一轮里模型可能输出好几次（先是一段“让我调用它”的独白 + ToolUseBlock，之后才是正式回答）。
                // 实时流式时这些全写进同一个气泡，历史也必须合并，否则刷新前后看到的不是一个东西
                if (assistant && turnBubble != null) {
                    String mergedId = extractMessageId(msg);
                    if (mergedId != null) {
                        // 合并时不会往 result 追加新元素，所以宿主气泡永远是最后一条
                        assistantIndices.add(result.size() - 1);
                        assistantMessageIds.add(mergedId);
                    }
                    mergeIntoTurnBubble(turnBubble, msg);
                    continue;
                }

                HistoryMessageVo vo = toHistoryVo(msg);
                result.add(vo);
                if (assistant) {
                    turnBubble = vo;
                    if (vo.getId() != null) {
                        assistantIndices.add(result.size() - 1);
                        assistantMessageIds.add(vo.getId());
                    }
                } else {
                    turnBubble = null;
                }
            }

            // 批量查产物：一条回答可能交付了多个文件，按 messageId 分组后整组挂上去
            if (!assistantMessageIds.isEmpty()) {
                Map<String, List<FileInfo>> artifactsByMessage = new HashMap<>();
                // listByMessageIds 已按创建时间升序，分组后组内顺序不变，前端不必再排
                for (MChatArtifact artifact : artifactManager.listByMessageIds(assistantMessageIds)) {
                    artifactsByMessage.computeIfAbsent(artifact.getMessageId(), key -> new ArrayList<>())
                            .add(FileInfo.from(artifact));
                }

                // 将产物附加到对应的 assistant 消息上：合并后的气泡 id 认的是最后一条回答，
                // 产物可能出自本轮更早的那条消息，所以按两个平行数组逐条对位，而不是只认气泡自己那条
                for (int i = 0; i < assistantIndices.size(); i++) {
                    int msgIndex = assistantIndices.get(i);
                    String messageId = assistantMessageIds.get(i);
                    List<FileInfo> artifacts = artifactsByMessage.get(messageId);
                    if (artifacts != null && !artifacts.isEmpty()) {
                        result.get(msgIndex).setArtifacts(artifacts);
                        log.debug("已将 {} 个产物附加到 messageId={} 的 assistant 消息",
                                artifacts.size(), messageId);
                    }
                }
            }

            log.info("成功加载 {} 条历史消息: userId={}, sessionId={}",
                    result.size(), userId, sessionId);

            // 人在回路：把还在等待确认的工具调用挂回最后一条回答，否则刷新后卡片丢失、会话只能卡在 ASKING
            attachPendingConfirms(userId, sessionId, result);

        } catch (Exception e) {
            log.error("加载会话历史失败: userId={}, sessionId={}, error={}",
                    userId, sessionId, e.getMessage(), e);
        }

        return result;
    }

    /**
     * 把会话当前的待确认工具调用挂到最后一条 assistant 消息上。
     * <p>挂在最后一条而不是新开一条：ASKING 的调用本来就出自这条回答，前端渲染时卡片能紧跟着那段文字，
     * 也不会多出一个凭空出现的助手气泡。
     * <p>失败不阻断历史加载（外层 try 已接住），宁可少一个确认入口也不要历史页面打不开。
     */
    private void attachPendingConfirms(String userId, String sessionId, List<HistoryMessageVo> result) {
        List<ConfirmRequest> pendingConfirms = conversationContextService.askingConfirmRequests(userId, sessionId);
        if (pendingConfirms.isEmpty()) {
            return;
        }
        for (int i = result.size() - 1; i >= 0; i--) {
            HistoryMessageVo vo = result.get(i);
            if (vo != null && "assistant".equalsIgnoreCase(vo.getRole())) {
                vo.setConfirmRequests(pendingConfirms);
                log.info("已将 {} 条待确认操作挂回最后一条回答: sessionId={}", pendingConfirms.size(), sessionId);
                return;
            }
        }
        log.warn("会话存在 {} 条待确认操作，但历史里没有可挂载的 assistant 消息: sessionId={}",
                pendingConfirms.size(), sessionId);
    }

    /**
     * 把一条上下文消息转成一个历史消息 VO。
     */
    private HistoryMessageVo toHistoryVo(Msg msg) {
        HistoryMessageVo.HistoryMessageVoBuilder builder = HistoryMessageVo.builder()
                .id(extractMessageId(msg))
                .role(msg.getRole().name().toLowerCase())
                .timestamp(msg.getTimestamp());

        String[] blocks = extractBlocks(msg);
        builder.content(blocks[0]);
        if (!blocks[1].isEmpty()) {
            builder.thinking(blocks[1]);
        }
        if (msg.getMetadata() != null && !msg.getMetadata().isEmpty()) {
            builder.metadata(msg.getMetadata());
        }
        return builder.build();
    }

    /**
     * 把同一轮里后续的模型输出合并进已有气泡，而不是新开一条。
     * <p>取值方式跟实时流式一致：后一轮的非空结果覆盖前一轮（{@code streamIntoMessage} 里
     * {@code messages[index].content = text} 就是覆盖写），这样刷新前后看到的是同一个气泡。
     * <p>id 与时间戳跟着最后一条消息走：它才是本轮真正的回答，也是会话标题、回滚点关心的对象。
     */
    private void mergeIntoTurnBubble(HistoryMessageVo bubble, Msg msg) {
        String[] blocks = extractBlocks(msg);
        if (!blocks[0].isEmpty()) {
            bubble.setContent(blocks[0]);
        }
        if (!blocks[1].isEmpty()) {
            bubble.setThinking(blocks[1]);
        }
        if (msg.getMetadata() != null && !msg.getMetadata().isEmpty()) {
            bubble.setMetadata(msg.getMetadata());
        }
        String messageId = extractMessageId(msg);
        if (messageId != null) {
            bubble.setId(messageId);
        }
        bubble.setTimestamp(msg.getTimestamp());
    }

    /**
     * 把一条 {@code role=TOOL} 消息里的工具结果折叠成调用痕迹，挂到本轮的助手气泡上。
     * <p>找不到宿主气泡（例如上下文被压缩掉了、或工具结果前一条是用户消息）就静默丢弃：
     * 这只是展示层信息，不该把历史页面弄坏。
     */
    private void foldToolResults(HistoryMessageVo bubble, Msg msg) {
        if (bubble == null || msg.getContent() == null) {
            return;
        }
        List<ToolCallVo> calls = bubble.getToolCalls() == null
                ? new ArrayList<>() : new ArrayList<>(bubble.getToolCalls());
        for (ContentBlock block : msg.getContent()) {
            if (block instanceof ToolResultBlock toolResult) {
                calls.add(ToolCallVo.builder()
                        .toolName(toolResult.getName())
                        .toolCallId(toolResult.getId())
                        .state(toolResult.getState() == null ? null : toolResult.getState().getValue())
                        .summary(summarizeOutput(toolResult.getOutput()))
                        .build());
            }
        }
        if (!calls.isEmpty()) {
            bubble.setToolCalls(calls);
        }
    }

    /**
     * 提取消息里的正文与思考内容。
     *
     * @return 长度为 2 的数组，下标 0 是 TextBlock 拼接的正文，下标 1 是 ThinkingBlock 拼接的思考
     */
    private static String[] extractBlocks(Msg msg) {
        StringBuilder textContent = new StringBuilder();
        StringBuilder thinkingContent = new StringBuilder();
        if (msg.getContent() != null) {
            for (ContentBlock block : msg.getContent()) {
                if (block instanceof TextBlock textBlock) {
                    textContent.append(textBlock.getText());
                } else if (block instanceof ThinkingBlock thinkingBlock) {
                    thinkingContent.append(thinkingBlock.getThinking());
                }
            }
        }
        return new String[]{textContent.toString(), thinkingContent.toString()};
    }

    /**
     * 工具输出可能很长（读文件、跑命令），截断规则与实时流共用 {@link ToolCallVo#summarize(String)}，
     * 免得刷新前后同一次调用显示出两种内容。
     */
    private static String summarizeOutput(List<ContentBlock> output) {
        if (output == null || output.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ContentBlock block : output) {
            if (block instanceof TextBlock textBlock) {
                sb.append(textBlock.getText());
            }
        }
        return ToolCallVo.summarize(sb.toString());
    }

    /**
     * 从 Msg 中提取消息ID。
     * 优先尝试 getId()，如果不存在则从 metadata 中获取。
     */
    private String extractMessageId(Msg msg) {
        // 尝试从 Msg 的 id 字段获取
        try {
            String id = msg.getId();
            if (id != null && !id.isBlank()) {
                return id;
            }
        } catch (Exception e) {
            // getId() 方法不存在，忽略
        }
        return null;
    }
}
