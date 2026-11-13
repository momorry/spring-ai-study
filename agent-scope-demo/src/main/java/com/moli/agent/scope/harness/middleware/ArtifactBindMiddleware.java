package com.moli.agent.scope.harness.middleware;

import com.moli.agent.scope.common.dao.manager.MChatArtifactManager;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.message.GenerateReason;
import io.agentscope.core.message.Msg;
import io.agentscope.core.middleware.AgentInput;
import io.agentscope.core.middleware.MiddlewareBase;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Function;

/**
 * 产物与消息绑定的中间件：本轮产出最终回答后，把这一轮交付的产物回填到那条回答的消息 ID 上。
 *
 * <p>为什么需要回填：产物是 {@code deliver_artifact} 在本轮答完<b>之前</b>交付的，
 * 那时最终 assistant 消息还没生成完，交付现场根本拿不到 {@code Msg.getId()}，
 * 所以 {@code m_chat_artifact.message_id} 只能先留空。而这条消息 ID 又是前端把产物挂到哪个气泡、
 * 历史加载时按 messageId 批量查产物的唯一依据，因此必须在轮末由这里补上。
 *
 * <p>只做绑定、不生成任何文件：产物的字节与台账都由 {@code ChatArtifactDeliveryTarget} 在交付时一次落好，
 * 本中间件不重复造产出链路，只负责把已落库的行归位。
 *
 * <p>门禁：只对“真正答完了”的那一轮回填。本轮被人在回路暂停时，上层拿到的结果消息带着
 * {@link GenerateReason#PERMISSION_ASKING} 之类的“未答完”原因，内容只是“让我调用工具”那段中间输出，
 * 若不拦住，产物会被钉在一条中间消息上，前端里它就再也取不回来了。
 */
@Slf4j
public class ArtifactBindMiddleware implements MiddlewareBase {

    /**
     * 本轮没有产出最终回答的原因：这些情况下 {@code AgentResultEvent} 里的是中间输出或被停下/中断的残体，
     * 不该据此回填。正常答完时该字段不下发，{@code Msg#getGenerateReason()} 缺省返回 MODEL_STOP，不在此列。
     */
    private static final Set<GenerateReason> TURN_NOT_FINISHED = EnumSet.of(
            GenerateReason.TOOL_CALLS,
            GenerateReason.TOOL_SUSPENDED,
            GenerateReason.REASONING_STOP_REQUESTED,
            GenerateReason.ACTING_STOP_REQUESTED,
            GenerateReason.MIDDLEWARE_STOP_REQUESTED,
            GenerateReason.PERMISSION_ASKING,
            GenerateReason.ALL_TOOLS_DENIED,
            GenerateReason.INTERRUPTED,
            GenerateReason.MAX_ITERATIONS);

    private final MChatArtifactManager artifactManager;

    public ArtifactBindMiddleware(MChatArtifactManager artifactManager) {
        this.artifactManager = artifactManager;
    }

    @Override
    public Flux<AgentEvent> onAgent(Agent agent, RuntimeContext ctx, AgentInput input,
                                    Function<AgentInput, Flux<AgentEvent>> next) {
        // 本轮起始时间：回填窗口的下界。交付落库用的 create_time 与本行同进程同时钟，比较结果可信
        LocalDateTime turnStart = LocalDateTime.now();
        Long userId = resolveUserId(ctx);
        String sessionId = ctx.getSessionId();

        return next.apply(input)
                .doOnNext(event -> {
                    if (!(event instanceof AgentResultEvent resultEvent)) {
                        return;
                    }
                    Msg result = resultEvent.getResult();
                    if (result == null) {
                        return;
                    }
                    // 本轮没答完（最常见的是命中 ASK 规则被暂停）：这段只是中间输出，不拿它的 id 去绑定
                    GenerateReason reason = result.getGenerateReason();
                    if (reason != null && TURN_NOT_FINISHED.contains(reason)) {
                        log.info("[ArtifactBind] 本轮未产出最终回答(generateReason={})，不回填产物: sessionId={}",
                                reason, sessionId);
                        return;
                    }
                    bindTurnArtifacts(userId, sessionId, result.getId(), turnStart);
                });
    }

    /**
     * 回填本轮产物。
     * <p>刻意在拿到最终回答的那一刻就做，而不是等流结束：上游还挂着 idle-timeout 与 onErrorResume，
     * 报错/超时的流不会走到 onComplete，放到那里回填等于把正常交付的产物也一起丢掉。
     * <p>失败只记日志：产物已经落库、能按会话列出、能下载，只是暂时没挂到消息上，不该拖垮正在流出的回答。
     */
    private void bindTurnArtifacts(Long userId, String sessionId, String messageId, LocalDateTime turnStart) {
        if (userId == null || sessionId == null || messageId == null || messageId.isBlank()) {
            log.warn("[ArtifactBind] 上下文缺少 userId/sessionId/messageId，跳过产物回填: sessionId={}, userId={}",
                    sessionId, userId);
            return;
        }
        try {
            int rows = artifactManager.bindMessageId(userId, sessionId, messageId, turnStart);
            if (rows > 0) {
                log.info("[ArtifactBind] 本轮产物已归位: messageId={}, 条数={}", messageId, rows);
            }
        } catch (Exception e) {
            log.warn("[ArtifactBind] 产物回填失败（不影响本次对话）: sessionId={}, messageId={}",
                    sessionId, messageId, e);
        }
    }

    /**
     * 解析归属用户。RuntimeContext 的 userId 是字符串，而台账的 user_id 是 bigint，
     * 非数字（比如匿名 id）就绑不上，否则会去改别人的行。
     */
    private Long resolveUserId(RuntimeContext ctx) {
        String raw = ctx == null ? null : ctx.getUserId();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            log.warn("[ArtifactBind] userId 不是数字，无法定位产物归属: userId={}", raw);
            return null;
        }
    }
}
