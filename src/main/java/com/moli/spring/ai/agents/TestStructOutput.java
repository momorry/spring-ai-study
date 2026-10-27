package com.moli.spring.ai.agents;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.moli.spring.ai.base.ChatModelUtil;
import lombok.Data;
import org.springframework.ai.chat.messages.AssistantMessage;

public class TestStructOutput {

    public static void main(String[] args) {
        ReactAgent agent = ReactAgent.builder()
                .name("poem_agent")
                .model(ChatModelUtil.getModel())
                .outputType(PoemOutput.class)
                .saver(new MemorySaver())
                .build();

        AssistantMessage response = null;
        try {
            response = agent.call("写一首关于春天的诗");
// 输出会遵循 PoemOutput 的结构
            System.out.println(response.getText());
        } catch (GraphRunnerException e) {
            throw new RuntimeException(e);
        }
    }

    @Data
    public static class PoemOutput {
        private String title;
        private String content;
        private String style;

    }
}
