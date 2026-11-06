//package com.moli.agent.scope.harness.agent;
//
//import cn.hutool.json.JSONUtil;
//import com.moli.agent.scope.common.base.SessionBase;
//import com.moli.agent.scope.common.dao.manager.MChatMessageFileManager;
//import com.moli.agent.scope.common.dao.manager.MChatSessionManager;
//import com.moli.agent.scope.common.vo.ChatStreamEvent;
//import com.moli.agent.scope.common.enums.MyAgentEvent;
//import com.moli.agent.scope.common.vo.FileInfo;
//import com.moli.agent.scope.common.vo.InConversationVo;
//import com.moli.agent.scope.harness.middleware.FileGenMiddleware;
//import io.agentscope.core.ReActAgent;
//import io.agentscope.core.agent.RuntimeContext;
//import io.agentscope.core.event.AgentEvent;
//import io.agentscope.core.event.AgentResultEvent;
//import io.agentscope.core.event.CustomEvent;
//import io.agentscope.core.event.TextBlockDeltaEvent;
//import io.agentscope.core.event.ThinkingBlockDeltaEvent;
//import io.agentscope.core.message.Msg;
//import io.agentscope.core.message.TextBlock;
//import io.agentscope.core.message.UserMessage;
//import io.agentscope.extensions.mysql.state.MysqlAgentStateStore;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.stereotype.Service;
//import reactor.core.publisher.Flux;
//
//@RequiredArgsConstructor
//@Service
//@Slf4j
//public class SimpleReActAgent {
//
//    private final ReActAgent reActAgent;
//
//    /**
//     * MySQL 会话状态存储（agentscope-extensions-mysql）。
//     * Agent 已绑定该 store，会按 RuntimeContext 的 userId/sessionId 自动持久化 / 恢复对话状态；
//     * 这里额外用它探测会话恢复情况。
//     */
//    private final MysqlAgentStateStore agentStateStore;
//
//    /**
//     * 历史会话列表业务表（m_chat_session）。
//     * 每次用户发起对话时 upsert 一行会话元数据（标题、最后消息预览、轮数），供前端侧边栏展示；
//     * 与 AgentScope 状态表通过 sessionId 关联。
//     */
//    private final MChatSessionManager chatSessionManager;
//
//    /**
//     * 消息文件产物关联表（m_chat_message_file）。
//     * 记录每次对话产生的文件产物，加载历史消息时根据 sessionId 查询并附加文件信息。
//     */
//    private final MChatMessageFileManager messageFileManager;
//
//    public Flux<ChatStreamEvent> conversation(InConversationVo conversationVo) {
//        log.info("========== 开始处理对话请求 ==========");
//        log.info("用户消息: {}", conversationVo.getMessage());
//        log.info("会话ID: {}", conversationVo.getSessionId());
//        log.info("用户ID: {}", conversationVo.getUserId());
//
//        UserMessage userMessage = UserMessage.builder()
//                .content(TextBlock.builder().text(conversationVo.getMessage()).build())
//                .build();
//        // userId 以 JWT 认证身份为准（Controller 已在请求线程写入 VO），为空时兜底
//        String userId = conversationVo.getUserId().toString();
//
//        // 记录/刷新历史会话列表元数据（m_chat_session）；失败不影响本次对话
//        try {
//            String sid = chatSessionManager.touchOnUserMessage(
//                    conversationVo.getUserId(), conversationVo.getSessionId(), conversationVo.getMessage());
//            conversationVo.setSessionId(sid);
//        } catch (Exception e) {
//            log.warn("记录历史会话列表失败（不影响本次对话）: {}", e.getMessage());
//        }
//
//
//        RuntimeContext rc = RuntimeContext.builder()
//                .sessionId(conversationVo.getSessionId())
//                .userId(userId)
//                .build();
//
//        log.info("开始调用 ReAct Agent 流式接口...");
//        // 关键修复：不在此处 subscribe()。streamEvents 返回的是冷流，
//        // 若这里订阅一次、再把它返回给 Spring MVC 订阅一次，Agent 会被执行两遍（两次真实模型调用）。
//        // 仅挂载日志算子后返回，由 MVC 订阅一次即可。
//        return reActAgent.streamEvents(userMessage, rc)
//                .doOnNext(event -> {
//                    log.info("【收到事件】类型: {}", event.getClass().getSimpleName());
//                    try {
//                        String eventJson = JSONUtil.toJsonPrettyStr(event);
//                        log.info("【事件详情】\n{}", eventJson);
//                    } catch (Exception e) {
//                        log.error("序列化事件失败: {}", e.getMessage());
//                        log.debug("【事件对象】{}", event);
//                    }
//                })
//                .map(event -> toStreamEvent(event, conversationVo.getSessionId()))
//                .doOnNext(event -> log.info("【发出事件】type={}, sessionId={}", event.getType(), event.getSessionId()))
//                .doOnComplete(() -> log.info("========== 流式响应完成 =========="))
//                .doOnError(error -> log.error("发生错误: {}", error.getMessage(), error));
//    }
//
//    /**
//     * 将 AgentScope SDK 的 AgentEvent 映射为自定义 ChatStreamEvent。
//     * <p>防腐层：隔离 SDK 内部结构变化对前端 API 协议的影响，同时支持扩展业务字段。
//     * <p>采用 instanceof 模式匹配直接 downcast 到具体子类，显式调用子类 getter，
//     * 避免反射带来的黑匣子开销与脆弱性。
//     */
//    private ChatStreamEvent toStreamEvent(AgentEvent event, String sessionId) {
//        // 1. CustomEvent（如文件产物生成事件）
//        if (event instanceof CustomEvent customEvent) {
//            return handleCustomEvent(customEvent, sessionId);
//        }
//
//        // 2. AgentResultEvent（最终聚合结果）
//        if (event instanceof AgentResultEvent resultEvent) {
//            return handleResultEvent(resultEvent, sessionId);
//        }
//
//        // 3. TextBlockDeltaEvent：文本增量
//        if (event instanceof TextBlockDeltaEvent textDelta) {
//            return ChatStreamEvent.builder()
//                    .type(MyAgentEvent.TEXT)
//                    .sessionId(sessionId)
//                    .delta(textDelta.getDelta())
//                    .build();
//        }
//
//        // 4. ThinkingBlockDeltaEvent：思考过程增量
//        if (event instanceof ThinkingBlockDeltaEvent thinkingDelta) {
//            return ChatStreamEvent.builder()
//                    .type(MyAgentEvent.THINKING)
//                    .sessionId(sessionId)
//                    .delta(thinkingDelta.getDelta())
//                    .build();
//        }
//
//        // 5. 其他 SDK 事件：保留原始类型信息，透传给前端按需消费
//        return ChatStreamEvent.builder()
//                .type(MyAgentEvent.OTHER)
//                .sessionId(sessionId)
//                .metadata(event.getType() != null
//                        ? java.util.Map.of("rawType", event.getType())
//                        : java.util.Map.of())
//                .build();
//    }
//
//    /**
//     * 处理 CustomEvent：目前识别 file_generated（文件产物已生成）。
//     */
//    private ChatStreamEvent handleCustomEvent(CustomEvent customEvent, String sessionId) {
//        String name = customEvent.getName();
//        if (FileGenMiddleware.EVENT_FILE_GENERATED.equals(name)) {
//            // 从 CustomEvent 中获取 FileInfo 对象（已序列化为 Map）
//            java.util.Map<String, Object> data = (java.util.Map<String, Object>) customEvent.getValue();
//            String filePath = (String) data.getOrDefault("fileUrl", "");
//            String messageId = (String) data.getOrDefault("messageId", "");
//            String fileName = (String) data.getOrDefault("fileName", "");
//            Long fileSize = data.get("fileSize") instanceof Long ? (Long) data.get("fileSize") : null;
//            String fileType = (String) data.getOrDefault("fileType", "text/plain");
//
//            FileInfo fileInfo = FileInfo.builder()
//                    .fileName(fileName)
//                    .fileUrl(filePath)
//                    .fileSize(fileSize)
//                    .fileType(fileType)
//                    .messageId(messageId)
//                    .build();
//
//            return ChatStreamEvent.builder()
//                    .type(MyAgentEvent.FILE)
//                    .sessionId(sessionId)
//                    .fileInfo(fileInfo)
//                    .build();
//        }
//        // 未识别的 CustomEvent
//        return ChatStreamEvent.builder()
//                .type(MyAgentEvent.OTHER)
//                .sessionId(sessionId)
//                .metadata(java.util.Map.of("customName", name))
//                .build();
//    }
//
//    /**
//     * 处理 AgentResultEvent：提取最终回复内容。
//     */
//    private ChatStreamEvent handleResultEvent(AgentResultEvent resultEvent, String sessionId) {
//        Msg result = resultEvent.getResult();
//        String text = result != null ? result.getTextContent() : null;
//        return ChatStreamEvent.builder()
//                .type(MyAgentEvent.RESULT)
//                .sessionId(sessionId)
//                .content(text)
//                .build();
//    }
//
//
//    public void convStop(SessionBase sessionBase) {
//        reActAgent.interrupt(sessionBase.getUserId().toString(),
//                sessionBase.getSessionId());
//    }
//
//}
