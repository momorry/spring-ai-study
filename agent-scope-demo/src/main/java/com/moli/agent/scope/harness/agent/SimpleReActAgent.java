package com.moli.agent.scope.harness.agent;

import cn.hutool.json.JSONUtil;
import com.moli.agent.scope.common.base.SessionBase;
import com.moli.agent.scope.common.dao.entity.MChatMessageFile;
import com.moli.agent.scope.common.dao.manager.MChatMessageFileManager;
import com.moli.agent.scope.common.dao.manager.MChatSessionManager;
import com.moli.agent.scope.common.vo.ChatStreamEvent;
import com.moli.agent.scope.common.vo.FileInfo;
import com.moli.agent.scope.common.vo.HistoryMessageVo;
import com.moli.agent.scope.common.vo.InConversationVo;
import com.moli.agent.scope.harness.middleware.FileGenMiddleware;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.CustomEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ThinkingBlockDeltaEvent;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ThinkingBlock;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.state.AgentState;
import io.agentscope.extensions.mysql.state.MysqlAgentStateStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Service
@Slf4j
public class SimpleReActAgent {

    private final ReActAgent reActAgent;

    /**
     * MySQL 会话状态存储（agentscope-extensions-mysql）。
     * Agent 已绑定该 store，会按 RuntimeContext 的 userId/sessionId 自动持久化 / 恢复对话状态；
     * 这里额外用它探测会话恢复情况。
     */
    private final MysqlAgentStateStore agentStateStore;

    /**
     * 历史会话列表业务表（m_chat_session）。
     * 每次用户发起对话时 upsert 一行会话元数据（标题、最后消息预览、轮数），供前端侧边栏展示；
     * 与 AgentScope 状态表通过 sessionId 关联。
     */
    private final MChatSessionManager chatSessionManager;

    /**
     * 消息文件产物关联表（m_chat_message_file）。
     * 记录每次对话产生的文件产物，加载历史消息时根据 sessionId 查询并附加文件信息。
     */
    private final MChatMessageFileManager messageFileManager;

    public Flux<ChatStreamEvent> conversation(InConversationVo conversationVo) {
        log.info("========== 开始处理对话请求 ==========");
        log.info("用户消息: {}", conversationVo.getMessage());
        log.info("会话ID: {}", conversationVo.getSessionId());
        log.info("用户ID: {}", conversationVo.getUserId());
        
        UserMessage userMessage = UserMessage.builder()
                .content(TextBlock.builder().text(conversationVo.getMessage()).build())
                .build();
        // userId 以 JWT 认证身份为准（Controller 已在请求线程写入 VO），为空时兜底
        String userId = conversationVo.getUserId().toString();

        // 记录/刷新历史会话列表元数据（m_chat_session）；失败不影响本次对话
        try {
            chatSessionManager.touchOnUserMessage(
                    conversationVo.getUserId(), conversationVo.getSessionId(), conversationVo.getMessage());
        } catch (Exception e) {
            log.warn("记录历史会话列表失败（不影响本次对话）: {}", e.getMessage());
        }


        RuntimeContext rc = RuntimeContext.builder()
                .sessionId(conversationVo.getSessionId())
                .userId(userId)
                .build();

        log.info("开始调用 ReAct Agent 流式接口...");
        // 关键修复：不在此处 subscribe()。streamEvents 返回的是冷流，
        // 若这里订阅一次、再把它返回给 Spring MVC 订阅一次，Agent 会被执行两遍（两次真实模型调用）。
        // 仅挂载日志算子后返回，由 MVC 订阅一次即可。
        return reActAgent.streamEvents(userMessage, rc)
                .doOnNext(event -> {
                    log.info("【收到事件】类型: {}", event.getClass().getSimpleName());
                    try {
                        String eventJson = JSONUtil.toJsonPrettyStr(event);
                        log.info("【事件详情】\n{}", eventJson);
                    } catch (Exception e) {
                        log.error("序列化事件失败: {}", e.getMessage());
                        log.debug("【事件对象】{}", event);
                    }
                })
                .map(event -> toStreamEvent(event, conversationVo.getSessionId()))
                .doOnNext(event -> log.info("【发出事件】type={}, sessionId={}", event.getType(), event.getSessionId()))
                .doOnComplete(() -> log.info("========== 流式响应完成 =========="))
                .doOnError(error -> log.error("发生错误: {}", error.getMessage(), error));
    }

    /**
     * 将 AgentScope SDK 的 AgentEvent 映射为自定义 ChatStreamEvent。
     * <p>防腐层：隔离 SDK 内部结构变化对前端 API 协议的影响，同时支持扩展业务字段。
     * <p>采用 instanceof 模式匹配直接 downcast 到具体子类，显式调用子类 getter，
     * 避免反射带来的黑匣子开销与脆弱性。
     */
    private ChatStreamEvent toStreamEvent(AgentEvent event, String sessionId) {
        // 1. CustomEvent（如文件产物生成事件）
        if (event instanceof CustomEvent customEvent) {
            return handleCustomEvent(customEvent, sessionId);
        }

        // 2. AgentResultEvent（最终聚合结果）
        if (event instanceof AgentResultEvent resultEvent) {
            return handleResultEvent(resultEvent, sessionId);
        }

        // 3. TextBlockDeltaEvent：文本增量
        if (event instanceof TextBlockDeltaEvent textDelta) {
            return ChatStreamEvent.builder()
                    .type("text")
                    .sessionId(sessionId)
                    .replyId(textDelta.getReplyId())
                    .blockId(textDelta.getBlockId())
                    .delta(textDelta.getDelta())
                    .build();
        }

        // 4. ThinkingBlockDeltaEvent：思考过程增量
        if (event instanceof ThinkingBlockDeltaEvent thinkingDelta) {
            return ChatStreamEvent.builder()
                    .type("thinking")
                    .sessionId(sessionId)
                    .replyId(thinkingDelta.getReplyId())
                    .blockId(thinkingDelta.getBlockId())
                    .delta(thinkingDelta.getDelta())
                    .build();
        }

        // 5. 其他 SDK 事件：保留原始类型信息，透传给前端按需消费
        return ChatStreamEvent.builder()
                .type("other")
                .sessionId(sessionId)
                .metadata(event.getType() != null
                        ? java.util.Map.of("rawType", event.getType())
                        : java.util.Map.of())
                .build();
    }

    /**
     * 处理 CustomEvent：目前识别 file_generated（文件产物已生成）。
     */
    private ChatStreamEvent handleCustomEvent(CustomEvent customEvent, String sessionId) {
        String name = customEvent.getName();
        if (FileGenMiddleware.EVENT_FILE_GENERATED.equals(name)) {
            java.util.Map<String, Object> data = customEvent.getValue();
            String filePath = (String) data.getOrDefault("filePath", "");
            // 从完整路径提取文件名
            String fileName = filePath.contains("\\")
                    ? filePath.substring(filePath.lastIndexOf('\\') + 1)
                    : filePath.contains("/")
                        ? filePath.substring(filePath.lastIndexOf('/') + 1)
                        : filePath;

            FileInfo fileInfo = FileInfo.builder()
                    .fileName(fileName)
                    .fileUrl(filePath)
                    .fileSize(null) // 如需文件大小，可在此读取文件属性
                    .fileType("text/plain")
                    .build();

            return ChatStreamEvent.builder()
                    .type("file")
                    .sessionId(sessionId)
                    .fileInfo(fileInfo)
                    .build();
        }
        // 未识别的 CustomEvent
        return ChatStreamEvent.builder()
                .type("other")
                .sessionId(sessionId)
                .metadata(java.util.Map.of("customName", name))
                .build();
    }

    /**
     * 处理 AgentResultEvent：提取最终回复内容。
     */
    private ChatStreamEvent handleResultEvent(AgentResultEvent resultEvent, String sessionId) {
        Msg result = resultEvent.getResult();
        String text = result != null ? result.getTextContent() : null;
        return ChatStreamEvent.builder()
                .type("result")
                .sessionId(sessionId)
                .content(text)
                .build();
    }


    public void convStop(SessionBase sessionBase) {
        reActAgent.interrupt(sessionBase.getUserId().toString(),
                sessionBase.getSessionId());
    }

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
        // 用于收集所有 assistant 消息的 replyId，以便批量查询文件产物
        List<String> assistantReplyIds = new ArrayList<>();
        // 记录每个 assistant 消息在 result 列表中的索引
        List<Integer> assistantIndices = new ArrayList<>();
        
        try {
            // 从 AgentStateStore 加载 AgentState
            Optional<AgentState> stateOpt = agentStateStore.get(
                    userId, sessionId, "agent_state", AgentState.class);
            
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
                
                // 提取 replyId（从 Msg 的 id 字段或 metadata 中获取）
                String replyId = extractReplyId(msg);
                
                HistoryMessageVo.HistoryMessageVoBuilder builder = HistoryMessageVo.builder()
                        .role(msg.getRole().name().toLowerCase())
                        .replyId(replyId)
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
                
                // 记录 assistant 消息的索引和 replyId
                if ("assistant".equalsIgnoreCase(msg.getRole().name()) && replyId != null) {
                    assistantIndices.add(result.size());
                    assistantReplyIds.add(replyId);
                }
                
                result.add(builder.build());
            }
            
            // 批量查询文件产物
            if (!assistantReplyIds.isEmpty()) {
                List<MChatMessageFile> fileRecords = messageFileManager.listByReplyIds(assistantReplyIds);
                // 构建 replyId -> fileInfo 的映射
                java.util.Map<String, FileInfo> fileInfoMap = new java.util.HashMap<>();
                for (MChatMessageFile fileRecord : fileRecords) {
                    FileInfo fileInfo = FileInfo.builder()
                            .fileName(fileRecord.getFileName())
                            .fileUrl(fileRecord.getFileUrl())
                            .fileSize(fileRecord.getFileSize())
                            .fileType(fileRecord.getFileType())
                            .build();
                    fileInfoMap.put(fileRecord.getReplyId(), fileInfo);
                }
                
                // 将文件产物信息附加到对应的 assistant 消息上
                for (int i = 0; i < assistantIndices.size(); i++) {
                    int msgIndex = assistantIndices.get(i);
                    String replyId = assistantReplyIds.get(i);
                    FileInfo fileInfo = fileInfoMap.get(replyId);
                    if (fileInfo != null) {
                        result.get(msgIndex).setFileInfo(fileInfo);
                        log.debug("已将文件产物 {} 附加到 replyId={} 的 assistant 消息",
                                fileInfo.getFileName(), replyId);
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
     * 从 Msg 中提取 replyId。
     * 优先尝试 getId()，如果不存在则从 metadata 中获取。
     */
    private String extractReplyId(Msg msg) {
        // 尝试从 Msg 的 id 字段获取
        try {
            String id = msg.getId();
            if (id != null && !id.isBlank()) {
                return id;
            }
        } catch (Exception e) {
            // getId() 方法不存在，忽略
        }
        // 尝试从 metadata 中获取
        if (msg.getMetadata() != null) {
            Object replyIdObj = msg.getMetadata().get("replyId");
            if (replyIdObj instanceof String replyId) {
                return replyId;
            }
            // 尝试其他可能的键名
            replyIdObj = msg.getMetadata().get("id");
            if (replyIdObj instanceof String replyId) {
                return replyId;
            }
        }
        return null;
    }
}
