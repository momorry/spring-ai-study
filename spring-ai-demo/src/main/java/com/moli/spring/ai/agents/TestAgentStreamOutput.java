package com.moli.spring.ai.agents;

import cn.hutool.core.util.RandomUtil;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.moli.spring.ai.base.ChatModelUtil;
import com.moli.spring.ai.hooks.LogHook;
import com.moli.spring.ai.hooks.MessageTrimmingHook;
import com.moli.spring.ai.interceptor.GuardrailInterceptor;
import com.moli.spring.ai.interceptor.ToolMonitoringInterceptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import reactor.core.publisher.Flux;

import java.util.concurrent.atomic.AtomicBoolean;

public class TestAgentStreamOutput {

    public static void main(String[] args) {

        try {
            ReactAgent reactAgent = ReactAgent.builder()
                    .name("poem_agent")
                    .model(ChatModelUtil.getModel())
                    .saver(new MemorySaver())
                    .hooks(new LogHook(), new MessageTrimmingHook())
                    .interceptors(new GuardrailInterceptor(), new ToolMonitoringInterceptor())
                    .build();

            String threadId = RandomUtil.randomString(8);
            RunnableConfig config = RunnableConfig.builder()
                    .addMetadata("k1", "v1")
                    .threadId(threadId)
                    .build();

            Flux<NodeOutput> stream = reactAgent.stream("帮我写一首诗，然后对这首诗进行鉴赏", config);
            AtomicBoolean stop = new AtomicBoolean(false);
            stream.subscribe(output -> {
                // 结合 OutputType 和消息类型进行处理
                if (output instanceof StreamingOutput streamingOutput) {
                    OutputType type = streamingOutput.getOutputType();
                    Message message = streamingOutput.message();

                    // 处理模型流式输出
                    if (type == OutputType.AGENT_MODEL_STREAMING) {
                        if (message instanceof AssistantMessage assistantMessage) {
                            // 检查是否为 Thinking 消息
                            Object reasoningContent = assistantMessage.getMetadata().get("reasoningContent");
                            if (reasoningContent != null && !reasoningContent.toString().isEmpty()) {
                                System.out.print("[Thinking] " + reasoningContent);
                            } else {
                                // 普通模型响应（增量内容）
                                System.out.print(assistantMessage.getText());
                            }
                        }
                    }
                    // 处理模型输出完成
                    else if (type == OutputType.AGENT_MODEL_FINISHED) {
                        if (message instanceof AssistantMessage assistantMessage) {
                            if (assistantMessage.hasToolCalls()) {
                                // 工具调用请求
                                assistantMessage.getToolCalls().forEach(toolCall -> {
                                    System.out.println("[Tool Call] " + toolCall.name() + ": " + toolCall.arguments());
                                });
                            } else {
                                // 模型完整响应
                                System.out.println("\n[Model Finished]");
                                stop.set(true);
                            }
                        }
                    }
                    // 处理工具执行结果
                    else if (type == OutputType.AGENT_TOOL_FINISHED) {
                        if (message instanceof ToolResponseMessage toolResponse) {
                            toolResponse.getResponses().forEach(response -> {
                                System.out.println("[Tool Result] " + response.name() + ": " + response.responseData());
                            });
                        }
                    } else {
                        System.out.println(output.state().data());
                    }
                }
            });
            while (!stop.get()) {
            }

        } catch (GraphRunnerException e) {
            throw new RuntimeException(e);
        }


    }
}
