package com.moli.agent.scope.service;

import com.moli.agent.scope.common.dao.entity.MChatMessageFile;
import com.moli.agent.scope.common.dao.manager.MChatMessageFileManager;
import com.moli.agent.scope.common.vo.FileInfo;
import com.moli.agent.scope.common.vo.HistoryMessageVo;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ThinkingBlock;
import io.agentscope.core.state.AgentState;
import io.agentscope.extensions.mysql.state.MysqlAgentStateStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
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
     * 消息文件产物关联表（m_chat_message_file）。
     * 记录每次对话产生的文件产物，加载历史消息时根据 sessionId 查询并附加文件信息。
     */
    private final MChatMessageFileManager messageFileManager;

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
        // 用于收集所有 assistant 消息的 messageId，以便批量查询文件产物
        List<String> assistantMessageIds = new ArrayList<>();
        // 记录每个 assistant 消息在 result 列表中的索引
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

            // 将 Msg 转换为 HistoryMessageVo
            for (Msg msg : context) {
                // 跳过 system 消息（系统提示词）
                if (msg.getRole() == null || "SYSTEM".equals(msg.getRole().name())) {
                    continue;
                }

                // 提取消息ID（从 Msg 的 id 字段）
                String messageId = extractMessageId(msg);

                HistoryMessageVo.HistoryMessageVoBuilder builder = HistoryMessageVo.builder()
                        .id(messageId)
                        .role(msg.getRole().name().toLowerCase())
                        .timestamp(msg.getTimestamp());

                // 提取文本内容和思考内容
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

                builder.content(textContent.toString());
                if (thinkingContent.length() > 0) {
                    builder.thinking(thinkingContent.toString());
                }

                // 提取元数据
                if (msg.getMetadata() != null && !msg.getMetadata().isEmpty()) {
                    builder.metadata(msg.getMetadata());
                }

                // 记录 assistant 消息的索引和 messageId
                if ("assistant".equalsIgnoreCase(msg.getRole().name()) && messageId != null) {
                    assistantIndices.add(result.size());
                    assistantMessageIds.add(messageId);
                }

                result.add(builder.build());
            }

            // 批量查询文件产物
            if (!assistantMessageIds.isEmpty()) {
                List<MChatMessageFile> fileRecords = messageFileManager.listByMessageIds(assistantMessageIds);
                // 构建 messageId -> fileInfo 的映射
                java.util.Map<String, FileInfo> fileInfoMap = new java.util.HashMap<>();
                for (MChatMessageFile fileRecord : fileRecords) {
                    FileInfo fileInfo = FileInfo.builder()
                            .fileName(fileRecord.getFileName())
                            .fileUrl(fileRecord.getFileUrl())
                            .fileSize(fileRecord.getFileSize())
                            .fileType(fileRecord.getFileType())
                            .build();
                    fileInfoMap.put(fileRecord.getMessageId(), fileInfo);
                }

                // 将文件产物信息附加到对应的 assistant 消息上
                for (int i = 0; i < assistantIndices.size(); i++) {
                    int msgIndex = assistantIndices.get(i);
                    String messageId = assistantMessageIds.get(i);
                    FileInfo fileInfo = fileInfoMap.get(messageId);
                    if (fileInfo != null) {
                        result.get(msgIndex).setFileInfo(fileInfo);
                        log.debug("已将文件产物 {} 附加到 messageId={} 的 assistant 消息",
                                fileInfo.getFileName(), messageId);
                    }
                }
            }

            log.info("成功加载 {} 条历史消息: userId={}, sessionId={}",
                    result.size(), userId, sessionId);

        } catch (Exception e) {
            log.error("加载会话历史失败: userId={}, sessionId={}, error={}",
                    userId, sessionId, e.getMessage(), e);
        }

        return result;
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
