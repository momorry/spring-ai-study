package com.moli.spring.ai.base;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

public class ChatModelUtil {
    private static final String DEFAULT_BASE_URL = "http://llm.test";

    private static final String DEFAULT_COMPLETIONS_PATH = "/chat/completions";

    private static final String DEFAULT_MODEL_NAME = "external-deepseek-v4-flash";

    private static final String API_KEY_ENV = "OPENAI_API_KEY";

    public static ChatModel getModel() {
        return createOpenAiModel();
    }

    private static ChatModel createOpenAiModel() {
        OpenAiApi openAiApi = OpenAiApi.builder()
                .apiKey("test")
                .baseUrl("http://llm.test")
                .completionsPath("/chat/completions")
                .build();

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model("external-deepseek-v4-flash")
                .temperature(0.7)
                .topP(0.9)
                .maxTokens(10000)
                .build();

        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(options)
                .build();
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("请先设置环境变量 " + name);
        }
        return value;
    }

    private static String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
