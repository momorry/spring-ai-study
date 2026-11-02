package com.moli.agent.scope.harness.model;

import io.agentscope.extensions.model.openai.OpenAIChatModel;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Data
@Component
public class ModelCreator {

    @Value("${llm.base-url}")
    private String baseUrl;
    @Value("${llm.model}")
    private String model;
    @Value("${llm.api-key}")
    private String apiKey;
    public OpenAIChatModel streamModel() {
        return OpenAIChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(model)
                .stream(true)               // 流式输出
                .build();
    }

    public OpenAIChatModel blockModel() {
        return OpenAIChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(model)
                .stream(false)
                .build();
    }
}
