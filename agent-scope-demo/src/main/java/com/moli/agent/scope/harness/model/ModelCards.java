package com.moli.agent.scope.harness.model;

import com.moli.agent.scope.common.util.JsonUtil;
import io.agentscope.core.credential.DeepSeekCredential;
import io.agentscope.core.credential.ModelCard;
import io.agentscope.extensions.model.openai.credential.OpenAICredential;

import java.util.List;

/**
 * 都还没有实现listModels
 */
public class ModelCards {


    public static void main(String[] args) {
        openai();
    }

    public static void openai() {
        OpenAICredential build = OpenAICredential.builder().baseUrl("1")
                .apiKey("1").build();
        List<ModelCard> block = build.listModels().block();
        for (ModelCard modelCard : block) {
            System.out.println(JsonUtil.toJson(modelCard));
        }
    }

    public static void deepseek() {
        DeepSeekCredential build = DeepSeekCredential.builder()
                .baseUrl("1")
                .apiKey("1")
                .build();
        List<ModelCard> block = build.listModels().block();
        for (ModelCard modelCard : block) {
            System.out.println(JsonUtil.toJson(modelCard));
        }
    }
}
