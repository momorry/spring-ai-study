package com.moli.spring.ai.agents;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.moli.spring.ai.base.ChatModelUtil;
import com.moli.spring.ai.hooks.LogHook;
import com.moli.spring.ai.hooks.MessageTrimmingHook;
import org.springframework.ai.chat.messages.Message;
import reactor.core.publisher.Flux;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public class TestAgentMemory {

    public static void main(String[] args) {

        try {
            ReactAgent reactAgent = ReactAgent.builder()
                    .name("poem_agent")
                    .model(ChatModelUtil.getModel())
                    .saver(new MemorySaver())
                    .build();

            String threadId = RandomUtil.randomString(8);
            System.out.println("threadId:" + threadId);
            RunnableConfig config = RunnableConfig.builder().addMetadata("k1", "v1")
                    .threadId(threadId)
                    .build();

            Optional<OverAllState> invoke = reactAgent.invoke("帮我写一首诗", config);
            OverAllState overAllState = invoke.get();
            System.out.println(JSONUtil.toJsonPrettyStr(overAllState.data()));

            Flux<NodeOutput> stream = reactAgent.stream("这首诗主要写的是什么？", config);
            AtomicBoolean stop = new AtomicBoolean(false);
            stream.subscribe(output -> {
                OutputType outputType = null;
                if (output instanceof StreamingOutput<?> streamingOutput) {
                    outputType = streamingOutput.getOutputType();
                    Message message = streamingOutput.message();
                    if(message == null) {
                        return;
                    }
                    String text = message.getText();
                    if(StrUtil.isNotBlank(text) && OutputType.AGENT_MODEL_STREAMING.equals(outputType)) {
                        System.out.print(text);
                    }
                } else {
                   System.out.println(output.state().data());
                }
                // 对于 Hook 节点，通常只关注完成事件（如果Hook没有有效输出可以忽略）
                if (outputType == OutputType.AGENT_MODEL_FINISHED) {
                    System.out.println("Hook 执行完成: " + output.node());
                    stop.set(true);
                }
            });
            while (!stop.get()) {
            }

        } catch (GraphRunnerException e) {
            throw new RuntimeException(e);
        }


    }
}
