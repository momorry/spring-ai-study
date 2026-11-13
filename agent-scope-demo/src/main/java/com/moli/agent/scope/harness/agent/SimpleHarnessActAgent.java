package com.moli.agent.scope.harness.agent;

import cn.hutool.json.JSONUtil;
import com.moli.agent.scope.common.base.SessionBase;
import com.moli.agent.scope.common.dao.entity.MChatArtifact;
import com.moli.agent.scope.common.dao.manager.MChatArtifactManager;
import com.moli.agent.scope.common.dao.manager.MChatSessionManager;
import com.moli.agent.scope.common.vo.ChatStreamEvent;
import com.moli.agent.scope.common.enums.MyAgentEvent;
import com.moli.agent.scope.common.vo.ConfirmDecision;
import com.moli.agent.scope.common.vo.ConfirmRequest;
import com.moli.agent.scope.common.vo.FileInfo;
import com.moli.agent.scope.common.vo.InConversationVo;
import com.moli.agent.scope.common.vo.ToolCallVo;
import com.moli.agent.scope.harness.artifact.ChatArtifactDeliveryTarget;
import com.moli.agent.scope.harness.middleware.DynamicModelMiddleware;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
@Service
@Slf4j
public class SimpleHarnessActAgent {

    /**
     * 工具输出摘要的内存上限：一次工具调用可能回几十万字（读文件、跑命令），
     * 展示只留前 {@link ToolCallVo#SUMMARY_MAX_LENGTH} 字，多攒一点仅为保留尾部换行便于 strip，没必要全存。
     */
    private static final int TOOL_OUTPUT_BUFFER_LIMIT = ToolCallVo.SUMMARY_MAX_LENGTH * 2;

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
     * 对话产物台账（m_chat_artifact）。
     * <p>交付工具的结果事件里只有文本摘要、没有 artifactId，所以本轮刚交付了什么只能反查台账；
     * 历史加载侧按 messageId 挂产物用的也是同一张表（见 MessageHistoryService）。
     */
    private final MChatArtifactManager artifactManager;

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
        List<ConfirmDecision> confirmDecisions = conversationVo.getConfirmResults();
        // 人在回路恢复：本次带的不是新提问，而是上一轮被 ASK 规则暂停的那些工具调用的裁决
        boolean resumeByConfirm = confirmDecisions != null && !confirmDecisions.isEmpty();
        log.info("待确认裁决: {} 条", resumeByConfirm ? confirmDecisions.size() : 0);

        // userId 以 JWT 认证身份为准（Controller 已在请求线程写入 VO），为空时兜底
        String userId = conversationVo.getUserId().toString();

        // 预先指定本轮用户消息 ID（不指则 SDK 会随机生成一个我们拿不到的）：
        // 一、重新生成需要按 ID 在上下文里精确定位回滚点（多条文本相同的消息只能靠 ID 区分）；
        // 二、该 ID 会随 DONE 事件回传前端，前端归到这条回答上，下次重新生成时原样带回
        String userMessageId = UUID.randomUUID().toString();
        UserMessage userMessage;
        if (resumeByConfirm) {
            // 拿 toolCallId 到会话上下文里找回那条 ASKING 调用并组装 ConfirmResult（入参以服务端原始值为准），
            // 对不上当场抛 BizException：此时 SSE 尚未建连，前端走非 2xx 分支提示，不会把垃圾 metadata 下发给框架
            List<ConfirmResult> confirmResults = conversationContextService.buildConfirmResults(
                    userId, conversationVo.getSessionId(), confirmDecisions);
            // 只挂 metadata、不带文本：框架在 ASKING 分支只读这条 metadata，不会把本次输入消息追加进上下文
            userMessage = UserMessage.builder()
                    .id(userMessageId)
                    .metadata(Map.of(Msg.METADATA_CONFIRM_RESULTS, confirmResults))
                    .build();
        } else {
            userMessage = UserMessage.builder()
                    .id(userMessageId)
                    .content(TextBlock.builder().text(conversationVo.getMessage()).build())
                    .build();
        }

        // 确认恢复不是一轮新对话：上下文里这条消息不会被追加，因此既不用回滚，也不该刷新会话列表预览
        if (!resumeByConfirm) {
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
        // 工具输出按调用 ID 累积：ToolResultEndEvent 本身不带输出内容，摘要只能从 TOOL_RESULT_TEXT_DELTA 增量里攒。
        // 放在方法局部而不是字段：本 Service 是单例，共用会把其他请求的工具输出串进当前气泡
        Map<String, StringBuilder> toolOutputs = new ConcurrentHashMap<>();
        // 本轮产物卡片的下发上下文，同理必须是每次调用各自一份
        // 起始时间取在订阅之前：交付落库只会晚于它，不会漏掉本轮任何一个产物
        ArtifactCardState artifactCards = new ArtifactCardState(
                conversationVo.getUserId(), streamSessionId, LocalDateTime.now(), ConcurrentHashMap.newKeySet());
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
                // flatMap 而非 map：一次交付工具的收尾除了工具调用痕迹本身，还要多下发一条产物卡片
                .flatMap(event -> Flux.fromIterable(toStreamEvents(event, streamSessionId, toolOutputs, artifactCards)))
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
                        // 确认恢复那次调用的消息 ID 不会落进上下文，下发反而会顶掉前端存着的真正回滚点
                        .userMessageId(resumeByConfirm ? null : userMessageId)
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
        // 会话停在 ASKING 时又发了新提问：框架会抛一大段面向开发者的 IllegalState（含恢复示例代码），
        // 对用户只会读到“确认卡片还没处理”，必须抢在其他关键字之前识别
        if (containsAny(chain, "human-in-the-loop confirmation", "asking state", "need your approval")) {
            return "上一步操作还在等你确认：请先在确认卡片中选择「允许本次」或「拒绝」，再继续提问";
        }
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
     * 一次 SDK 事件 → 零到多条下发事件。
     * <p>主事件照旧由 {@link #toStreamEvent} 映射（每个事件至少产出一条，前端不会拿到空帧）；
     * 交付工具收尾时额外补一条 FILE 产物卡片，排在主事件之后：先让用户看见这一步做了什么，再看见产物。
     */
    private List<ChatStreamEvent> toStreamEvents(AgentEvent event, String sessionId,
                                                 Map<String, StringBuilder> toolOutputs,
                                                 ArtifactCardState artifactCards) {
        ChatStreamEvent primary = toStreamEvent(event, sessionId, toolOutputs);
        List<FileInfo> delivered = newlyDeliveredArtifacts(event, artifactCards);
        if (delivered.isEmpty()) {
            return List.of(primary);
        }
        List<ChatStreamEvent> events = new ArrayList<>(2);
        events.add(primary);
        events.add(ChatStreamEvent.builder()
                .type(MyAgentEvent.FILE)
                .sessionId(sessionId)
                .artifacts(delivered)
                .build());
        return events;
    }

    /**
     * 本轮里“这次调用之后新交付”的产物，取过即记入已下发集合，避免重复发卡。
     * <p>为什么不去解析工具返回的文本：{@code deliver_artifact} 的返回是给模型看的一句话摘要，
     * 从里面抓 artifactId 等于把前端协议绑在了一句提示文案上；而且模型一轮可以交好几个文件，
     * 一次收尾对不上具体是哪几个。台账在交付返回之前就已经落好，所以按「会话 + 本轮起始时间」反查最可靠。
     * <p>也因此不需要判工具结果状态：交付真失败时压根没有新行，查出来就是空，自然不发卡片。
     */
    private List<FileInfo> newlyDeliveredArtifacts(AgentEvent event, ArtifactCardState artifactCards) {
        if (!(event instanceof ToolResultEndEvent toolResultEnd)
                || !ChatArtifactDeliveryTarget.DELIVER_TOOL_NAME.equals(toolResultEnd.getToolCallName())) {
            return List.of();
        }
        List<FileInfo> fresh = new ArrayList<>();
        try {
            List<MChatArtifact> delivered = artifactManager.listSince(
                    artifactCards.userId(), artifactCards.sessionId(), artifactCards.turnStart());
            for (MChatArtifact artifact : delivered) {
                if (artifact.getId() != null && artifactCards.emitted().add(artifact.getId())) {
                    fresh.add(FileInfo.from(artifact));
                }
            }
        } catch (Exception e) {
            // 卡片只是展示用的投影：查失败不能把本轮回答一起拖挂，刷新后从历史加载仍能看见产物
            log.warn("【产物卡片】本轮交付反查失败（不影响本次对话）: sessionId={}, {}",
                    artifactCards.sessionId(), e.getMessage());
        }
        if (!fresh.isEmpty()) {
            log.info("【产物卡片】本轮新下发 {} 个: {}", fresh.size(),
                    fresh.stream().map(FileInfo::getFileName).toList());
        }
        return fresh;
    }

    /**
     * 本次调用的产物卡片下发状态（每次调用各自一份，不能用字段：单例 Service 共用会把产物串到其他气泡）。
     *
     * @param emitted 已下发过的产物ID，兼作去重集
     */
    private record ArtifactCardState(Long userId, String sessionId, LocalDateTime turnStart, Set<Long> emitted) {
    }

    /**
     * 将 AgentScope SDK 的 AgentEvent 映射为自定义 ChatStreamEvent。
     * <p>防腐层：隔离 SDK 内部结构变化对前端 API 协议的影响，同时支持扩展业务字段。
     * <p>采用 instanceof 模式匹配直接 downcast 到具体子类，显式调用子类 getter，
     * 避免反射带来的黑匣子开销与脆弱性。
     *
     * @param toolOutputs 本次请求内的工具输出累积器，以 toolCallId 为键；
     *                    增量事件往里写，收尾事件取完即删
     */
    private ChatStreamEvent toStreamEvent(AgentEvent event, String sessionId,
                                          Map<String, StringBuilder> toolOutputs) {
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

        // 5. RequireUserConfirmEvent：人在回路——命中 ASK 规则的工具被标成 ASKING 后本轮已暂停，
        //    只有把这些待确认项下发给前端，会话才有机会被 ConfirmResult 推下去
        if (event instanceof RequireUserConfirmEvent confirmEvent) {
            List<ConfirmRequest> requests = conversationContextService.toConfirmRequests(confirmEvent.getToolCalls());
            log.info("【等待确认】replyId={}, 待确认 {} 条: {}", confirmEvent.getReplyId(), requests.size(),
                    requests.stream().map(ConfirmRequest::getToolName).toList());
            return ChatStreamEvent.builder()
                    .type(MyAgentEvent.CONFIRM)
                    .sessionId(sessionId)
                    .confirmRequests(requests)
                    .metadata(confirmEvent.getReplyId() == null
                            ? Map.of() : Map.of("replyId", confirmEvent.getReplyId()))
                    .build();
        }

        // 6. ToolResultTextDeltaEvent：工具输出增量，只用来攒摘要，不单独下发（卡片等收尾时一次性给出完整内容，
        //    与历史加载那一份展示对齐）
        if (event instanceof ToolResultTextDeltaEvent toolDelta) {
            collectToolOutput(toolOutputs, toolDelta.getToolCallId(), toolDelta.getDelta());
            return ChatStreamEvent.builder()
                    .type(MyAgentEvent.OTHER)
                    .sessionId(sessionId)
                    .metadata(java.util.Map.of("rawType", String.valueOf(event.getType())))
                    .build();
        }

        // 7. ToolResultEndEvent：一次工具调用收尾。下发名称/调用 ID/结果状态/输出摘要，前端把它折成工具调用痕迹，
        //    这样不刷新页面也能看到本轮干了什么；历史加载侧走的是同一字段（见 MessageHistoryService）
        if (event instanceof ToolResultEndEvent toolResultEnd) {
            ToolCallVo toolCall = ToolCallVo.builder()
                    .toolName(toolResultEnd.getToolCallName())
                    .toolCallId(toolResultEnd.getToolCallId())
                    .state(toolResultEnd.getState() == null ? null : toolResultEnd.getState().getValue())
                    .summary(ToolCallVo.summarize(takeToolOutput(toolOutputs, toolResultEnd.getToolCallId())))
                    .build();
            return ChatStreamEvent.builder()
                    .type(MyAgentEvent.TOOL)
                    .sessionId(sessionId)
                    .toolCalls(List.of(toolCall))
                    .build();
        }

        // 8. 其他 SDK 事件：保留原始类型信息，透传给前端按需消费
        return ChatStreamEvent.builder()
                .type(MyAgentEvent.OTHER)
                .sessionId(sessionId)
                .metadata(event.getType() != null
                        ? java.util.Map.of("rawType", event.getType())
                        : java.util.Map.of())
                .build();
    }

    /**
     * 处理 CustomEvent：未识别的先透传原始类型，不强行编出一个业务事件。
     * <p>产物卡片不在这里：产物由 {@link #newlyDeliveredArtifacts} 在交付工具收尾时从台账反查下发，
     * 不依赖中间件往事件流里塞自定义事件（消息流会被压缩/重新生成改写，自定义事件本来也不落库）。
     */
    private ChatStreamEvent handleCustomEvent(CustomEvent customEvent, String sessionId) {
        return ChatStreamEvent.builder()
                .type(MyAgentEvent.OTHER)
                .sessionId(sessionId)
                .metadata(java.util.Map.of("customName", String.valueOf(customEvent.getName())))
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


    /**
     * 逐段攒下工具输出，供收尾事件算摘要。增量可能很多段，超上限后不再追写。
     */
    private static void collectToolOutput(Map<String, StringBuilder> toolOutputs, String toolCallId, String delta) {
        if (toolCallId == null || toolCallId.isBlank() || delta == null || delta.isEmpty()) {
            return;
        }
        StringBuilder buffer = toolOutputs.computeIfAbsent(toolCallId, key -> new StringBuilder());
        synchronized (buffer) {
            if (buffer.length() < TOOL_OUTPUT_BUFFER_LIMIT) {
                buffer.append(delta);
            }
        }
    }

    /**
     * 取走并移除某次调用的累积输出（一次调用只会收尾一次，不删会一直占着内存）。
     */
    private static String takeToolOutput(Map<String, StringBuilder> toolOutputs, String toolCallId) {
        if (toolCallId == null) {
            return "";
        }
        StringBuilder buffer = toolOutputs.remove(toolCallId);
        return buffer == null ? "" : buffer.toString();
    }


    public void convStop(SessionBase sessionBase) {
        harnessAgent.interrupt(sessionBase.getUserId().toString(),
                sessionBase.getSessionId());
    }


}
