package com.moli.agent.scope.harness.agent;

import cn.hutool.json.JSONUtil;
import com.moli.agent.scope.common.base.SessionBase;
import com.moli.agent.scope.common.dao.manager.MChatMessageFileManager;
import com.moli.agent.scope.common.dao.manager.MChatSessionManager;
import com.moli.agent.scope.common.vo.ChatStreamEvent;
import com.moli.agent.scope.common.enums.MyAgentEvent;
import com.moli.agent.scope.common.vo.FileInfo;
import com.moli.agent.scope.common.vo.InConversationVo;
import com.moli.agent.scope.harness.middleware.DynamicModelMiddleware;
import com.moli.agent.scope.harness.middleware.FileGenMiddleware;
import com.moli.agent.scope.service.ConversationContextService;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.*;
import io.agentscope.core.message.*;
import io.agentscope.extensions.mysql.state.MysqlAgentStateStore;
import io.agentscope.harness.agent.HarnessAgent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Locale;
import java.util.UUID;

@RequiredArgsConstructor
@Service
@Slf4j
public class SimpleHarnessActAgent {

    private final HarnessAgent harnessAgent;

    /**
     * 流式事件空闲超时（秒）：模型或工具长时间不产出任何事件时主动断流，
     * 由 {@link #resumeFromError} 转成 ERROR 事件下发，避免前端连接一直挂着、界面卡在 loading。
     */
    @Value("${agent.stream.idle-timeout-seconds:300}")
    private long idleTimeoutSeconds;

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

    /**
     * 会话上下文写侧操作：重新生成前需要先把要重放的那一轮回滚掉（见 {@link ConversationContextService}）。
     */
    private final ConversationContextService conversationContextService;

    public Flux<ChatStreamEvent> conversation(InConversationVo conversationVo) {
        log.info("========== 开始处理对话请求 ==========");
        log.info("用户消息: {}", conversationVo.getMessage());
        log.info("会话ID: {}", conversationVo.getSessionId());
        log.info("用户ID: {}", conversationVo.getUserId());
        log.info("请求模型: {}", StringUtils.defaultIfBlank(conversationVo.getModelName(), "(未指定，使用默认模型)"));
        log.info("是否重新生成: {}, 重放的目标消息 ID: {}", conversationVo.isRegenerate(),
                StringUtils.defaultIfBlank(conversationVo.getRegenerateMessageId(), "(未传)"));

        // 预先指定本轮用户消息 ID（不指则 SDK 会随机生成一个我们拿不到的）：
        // 一、重新生成需要按 ID 在上下文里精确定位回滚点（多条文本相同的消息只能靠 ID 区分）；
        // 二、该 ID 会随 DONE 事件回传前端，前端归到这条回答上，下次重新生成时原样带回
        String userMessageId = UUID.randomUUID().toString();
        UserMessage userMessage = UserMessage.builder()
                .id(userMessageId)
                .content(TextBlock.builder().text(conversationVo.getMessage()).build())
                .build();
        // userId 以 JWT 认证身份为准（Controller 已在请求线程写入 VO），为空时兜底
        String userId = conversationVo.getUserId().toString();

        // 重新生成：先把上下文回滚到这条用户消息之前，再重放它。
        // 必须排在 streamEvents 之前（Agent 一跑就会把这条消息追加回上下文），也排在会话元数据刷新之前。
        // 不捕获异常：定位不到回滚点就当场失败，让它冒到 HTTP 响应（此时 SSE 尚未建连，
        // 前端走 !response.ok 分支）；若退化为直接追加，会把上下文静默改脏，比报错更难排查
        if (conversationVo.isRegenerate()) {
            conversationContextService.truncateForRegenerate(
                    userId, conversationVo.getSessionId(),
                    conversationVo.getRegenerateMessageId(), conversationVo.getMessage());
        }

        // 记录/刷新历史会话列表元数据（m_chat_session）；失败不影响本次对话
        try {
            String sid = chatSessionManager.touchOnUserMessage(
                    conversationVo.getUserId(), conversationVo.getSessionId(), conversationVo.getMessage(),
                    conversationVo.isRegenerate());
            conversationVo.setSessionId(sid);
        } catch (Exception e) {
            log.warn("记录历史会话列表失败（不影响本次对话）: {}", e.getMessage());
        }


        RuntimeContext.Builder contextBuilder = RuntimeContext.builder()
                .sessionId(conversationVo.getSessionId())
                .userId(userId);
        // 把本次选定的模型随上下文下发：DynamicModelMiddleware 在每次模型调用前据此替换模型实例
        // （Agent 为单例且 model 字段不可变，只能按调用维度传递，不能用字段保存）
        if (StringUtils.isNotBlank(conversationVo.getModelName())) {
            contextBuilder.put(DynamicModelMiddleware.MODEL_NAME_KEY, conversationVo.getModelName().trim());
        }
        RuntimeContext rc = contextBuilder.build();

        log.info("开始调用 ReAct Agent 流式接口...");
        final String streamSessionId = conversationVo.getSessionId();
        // 关键修复：不在此处 subscribe()。streamEvents 返回的是冷流，
        // 若这里订阅一次、再把它返回给 Spring MVC 订阅一次，Agent 会被执行两遍（两次真实模型调用）。
        // 仅挂载日志算子后返回，由 MVC 订阅一次即可。
        // defer 包裹：streamEvents 若在装配阶段同步抛异常，也能变成流内 error 信号被下面的 onErrorResume 兜住。
        return Flux.defer(() -> harnessAgent.streamEvents(userMessage, rc))
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
                .map(event -> toStreamEvent(event, streamSessionId))
                // 长时间无事件（模型卡死、工具 hang）→ TimeoutException，同样交给 onErrorResume 收敛
                .timeout(Duration.ofSeconds(idleTimeoutSeconds))
                // 关键：SSE 一旦建连（200 + text/event-stream），异常绝不能再以 error 信号抛出——
                // 前端读不到任何业务事件，只能等服务端异步超时断连，界面会一直停在 loading；
                // 这里把异常翻译成 ERROR 事件下发，让流以「正常完成」收尾，前端拿到事件即可复位状态。
                .onErrorResume(error -> resumeFromError(error, streamSessionId))
                // 成功、出错、被中断三条路径都补一个 DONE 终止事件，给前端明确的结束信号；
                // 同时把本轮用户消息 ID 一并下发，前端存下来供下次按 ID 精确定位重新生成的回滚点
                .concatWith(Flux.just(ChatStreamEvent.builder()
                        .type(MyAgentEvent.DONE)
                        .sessionId(streamSessionId)
                        .userMessageId(userMessageId)
                        .build()))
                .doOnNext(event -> log.info("【发出事件】type={}, sessionId={}", event.getType(), event.getSessionId()))
                .doOnComplete(() -> log.info("========== 流式响应完成 =========="));
    }

    /**
     * 流内异常的收敛出口：保证 SSE 流在任何情况下都能正常结束。
     * <p>用户主动「停止生成」不属于故障，只收尾不额外报错；其余异常统一发 ERROR 事件。
     */
    private Flux<ChatStreamEvent> resumeFromError(Throwable error, String sessionId) {
        log.error("流式对话异常: {}", error.getMessage(), error);
        if (isInterrupted(error)) {
            log.info("本次生成已被中断，仅正常收尾");
            return Flux.empty();
        }
        return Flux.just(toErrorEvent(error, sessionId));
    }

    /**
     * 构建 ERROR 事件：{@code errorMessage} 给出用户可执行的提示，{@code metadata} 保留原始异常便于排查。
     */
    private ChatStreamEvent toErrorEvent(Throwable error, String sessionId) {
        Throwable root = rootCause(error);
        String rawMessage = StringUtils.defaultIfBlank(root.getMessage(), "无详细信息");
        return ChatStreamEvent.builder()
                .type(MyAgentEvent.ERROR)
                .sessionId(sessionId)
                .errorMessage(resolveFriendlyMessage(error))
                .metadata(java.util.Map.of(
                        "errorType", root.getClass().getSimpleName(),
                        "rawMessage", StringUtils.abbreviate(rawMessage, 300)))
                .build();
    }

    /**
     * 异常 → 用户可读提示。
     * <p>基于「整条 cause 链的类名 + 报文文本」做关键字判定，而不 import SDK 异常类：
     * 各模型网关错误码并不统一（额度用完可能返回 403，也可能返回 429），
     * 文本匹配比绑定 {@code PermissionDeniedException} 之类的具体类型更耐变化。
     */
    private String resolveFriendlyMessage(Throwable error) {
        String chain = causeChainText(error);
        if (containsAny(chain, "额度", "欠费", "quota", "insufficient balance", "arrears",
                "permissiondenied", "status 403")) {
            return "模型服务 Token 额度已用完（或账号欠费），请切换到其他可用模型，或联系服务方充值后重试";
        }
        if (containsAny(chain, "status 429", "ratelimit", "rate limit", "too many requests")) {
            return "模型服务请求过于频繁已被限流，请稍后重试";
        }
        if (containsAny(chain, "status 401", "authentication", "invalid api key", "api key")) {
            return "模型 API Key 无效或已过期，请检查服务端模型配置";
        }
        if (containsAny(chain, "status 404", "notfound", "model not found")) {
            return "请求的模型不存在或已下线，请更换模型后重试";
        }
        if (containsAny(chain, "context length", "context_length", "maximum context", "too many tokens")) {
            return "本次对话上下文超出模型长度限制，请新开会话或缩短本次提问后重试";
        }
        if (containsAny(chain, "unknownhost", "no such host", "connection refused", "connection reset",
                "connectexception", "socket", "ssl", "cert")) {
            return "无法连接模型服务，请检查服务端网络或模型 base-url 配置";
        }
        if (containsAny(chain, "internalserver", "status 500", "status 502", "status 503",
                "service unavailable", "badgateway")) {
            return "模型服务暂时不可用（上游 5xx），请稍后重试";
        }
        if (containsAny(chain, "timeout", "timed out", "did not observe any item")) {
            return "模型服务超过 " + idleTimeoutSeconds + " 秒无响应，本次生成已中断，请重试";
        }
        return "对话服务异常：" + StringUtils.abbreviate(
                StringUtils.defaultIfBlank(rootCause(error).getMessage(), "无详细信息"), 200);
    }

    /** Agent 被主动中断（点「停止生成」）时不该向用户报错，只需让流正常结束 */
    private boolean isInterrupted(Throwable error) {
        return containsAny(causeChainText(error), "interrupt", "cancel", "stop requested");
    }

    /** 收集 cause 链上各层的「类名 + message」并转小写，供关键字判定 */
    private String causeChainText(Throwable error) {
        StringBuilder sb = new StringBuilder();
        Throwable cur = error;
        for (int depth = 0; cur != null && depth < 10; depth++) {
            sb.append(cur.getClass().getSimpleName()).append(' ')
                    .append(StringUtils.defaultString(cur.getMessage())).append(' ');
            cur = cur.getCause() == cur ? null : cur.getCause();
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    /** 取最内层 cause（各层 provider 包装的原始报文通常在链路末端最有信息量） */
    private Throwable rootCause(Throwable error) {
        Throwable cur = error;
        for (int depth = 0; cur.getCause() != null && cur.getCause() != cur && depth < 10; depth++) {
            cur = cur.getCause();
        }
        return cur;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
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
                    .type(MyAgentEvent.TEXT)
                    .sessionId(sessionId)
                    .delta(textDelta.getDelta())
                    .build();
        }

        // 4. ThinkingBlockDeltaEvent：思考过程增量
        if (event instanceof ThinkingBlockDeltaEvent thinkingDelta) {
            return ChatStreamEvent.builder()
                    .type(MyAgentEvent.THINKING)
                    .sessionId(sessionId)
                    .delta(thinkingDelta.getDelta())
                    .build();
        }

        // 5. 其他 SDK 事件：保留原始类型信息，透传给前端按需消费
        return ChatStreamEvent.builder()
                .type(MyAgentEvent.OTHER)
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
            // 从 CustomEvent 中获取 FileInfo 对象（已序列化为 Map）
            java.util.Map<String, Object> data = customEvent.getValue();
            String filePath = (String) data.getOrDefault("fileUrl", "");
            String messageId = (String) data.getOrDefault("messageId", "");
            String fileName = (String) data.getOrDefault("fileName", "");
            Long fileSize = data.get("fileSize") instanceof Long ? (Long) data.get("fileSize") : null;
            String fileType = (String) data.getOrDefault("fileType", "text/plain");

            FileInfo fileInfo = FileInfo.builder()
                    .fileName(fileName)
                    .fileUrl(filePath)
                    .fileSize(fileSize)
                    .fileType(fileType)
                    .messageId(messageId)
                    .build();

            return ChatStreamEvent.builder()
                    .type(MyAgentEvent.FILE)
                    .sessionId(sessionId)
                    .fileInfo(fileInfo)
                    .build();
        }
        // 未识别的 CustomEvent
        return ChatStreamEvent.builder()
                .type(MyAgentEvent.OTHER)
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
                .type(MyAgentEvent.RESULT)
                .sessionId(sessionId)
                .content(text)
                .build();
    }


    public void convStop(SessionBase sessionBase) {
        harnessAgent.interrupt(sessionBase.getUserId().toString(),
                sessionBase.getSessionId());
    }


}
