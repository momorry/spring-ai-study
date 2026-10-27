package com.moli.agent.scope.model;

import io.agentscope.extensions.model.openai.OpenAIChatModel;

public class Model {

    public static OpenAIChatModel streamModel() {
        return OpenAIChatModel.builder()
                .apiKey("test")
                .baseUrl("http://llm.test")
                .modelName("external-deepseek-v4-flash")
                .stream(true)               // 流式输出
                .build();
    }

    public static OpenAIChatModel blockModel() {
        return OpenAIChatModel.builder()
                .apiKey("test")
                .baseUrl("http://llm.test")
                .modelName("external-deepseek-v4-flash")
                .stream(false)
                .build();
    }
}
