package com.moli.agent.scope;

import com.moli.agent.scope.middleware.FileGenMiddleware;
import com.moli.agent.scope.model.Model;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.CustomEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.state.InMemoryAgentStateStore;
import reactor.core.publisher.Flux;

import java.util.List;

public class AgentSession {

    public static void main(String[] args) {

        ReActAgent agent = ReActAgent.builder()
                .model(Model.blockModel())
                .name("ai")
                .sysPrompt("你是一个有趣的AI助手")
                .stateStore(new InMemoryAgentStateStore())
                .middleware(new FileGenMiddleware())
                .build();

        // 第一轮对话：alice / session-1
        System.out.println("===== 第一轮对话 =====");
        runStreamEvents(agent, "你好", "alice", "session-1");

        // 第二轮对话：bob / session-2
        System.out.println("\n===== 第二轮对话 =====");
        runStreamEvents(agent, "Hi here", "bob", "session-2");
    }

    /**
     * 使用 streamEvents 进行流式事件调用，
     * 从事件流中提取 AI 回复文本和产物文件地址。
     */
    private static void runStreamEvents(ReActAgent agent, String userInput,
                                        String userId, String sessionId) {
        RuntimeContext ctx = RuntimeContext.builder()
                .userId(userId).sessionId(sessionId)
                .build();

        Flux<AgentEvent> eventFlux = agent.streamEvents(
                List.of(new UserMessage(userInput)), ctx);

        // 阻塞订阅事件流，处理每种事件类型
        eventFlux.doOnNext(event -> {
            // AI 最终回复
            if (event instanceof AgentResultEvent resultEvent) {
                Msg result = resultEvent.getResult();
                System.out.println("[AI回复]: " + result.getTextContent());
            }
            // 产物文件生成事件
            else if (event instanceof CustomEvent customEvent
                    && FileGenMiddleware.EVENT_FILE_GENERATED.equals(customEvent.getName())) {
                String filePath = (String) customEvent.getValue().get("filePath");
                System.out.println("[产物地址]: " + filePath);
            }
        }).blockLast(); // 阻塞等待所有事件处理完成
    }
}
