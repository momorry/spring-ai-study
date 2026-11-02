package com.moli.agent.scope.harness.agent;

import cn.hutool.json.JSONUtil;
import com.moli.agent.scope.common.vo.InConversationVo;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.UserMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@RequiredArgsConstructor
@Service
@Slf4j
public class SimpleReActAgent {

    private final ReActAgent reActAgent;

    public Flux<AgentEvent> conv(InConversationVo conversationVo) {
        log.info("========== 开始处理对话请求 ==========");
        log.info("用户消息: {}", conversationVo.getMessage());
        log.info("会话ID: {}", conversationVo.getSessionId());
        log.info("用户ID: {}", conversationVo.getUserId());
        
        UserMessage userMessage = UserMessage.builder()
                .content(TextBlock.builder().text(conversationVo.getMessage()).build())
                .build();
        RuntimeContext rc = RuntimeContext.builder().sessionId(conversationVo.getSessionId())
                .userId(conversationVo.getUserId().toString()).build();
        
        log.info("开始调用 ReAct Agent 流式接口...");
        Flux<AgentEvent> agentEventFlux = reActAgent.streamEvents(userMessage, rc);
        
        // 订阅并打印流式消息（详细 JSON）
        agentEventFlux
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
                .doOnComplete(() -> log.info("========== 流式响应完成 =========="))
                .doOnError(error -> log.error("发生错误: {}", error.getMessage(), error))
                .subscribe();
        
        log.info("返回 Flux 流给调用方");
        return agentEventFlux;
    }
}
