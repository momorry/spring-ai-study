package com.moli.spring.ai.interceptor;

import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;

import java.util.List;

public class GuardrailInterceptor extends ModelInterceptor {

    @Override
    public ModelResponse interceptModel(ModelRequest request, ModelCallHandler handler) {
        // 前置：检查输入
        if (containsSensitiveContent(request.getMessages())) {
            return ModelResponse.of(AssistantMessage.builder().content("检测到不适当的内容").build());
        }

        // 执行调用
        ModelResponse response = handler.call(request);

        // 后置：检查输出
        return sanitizeIfNeeded(response);
    }

    private ModelResponse sanitizeIfNeeded(ModelResponse response) {
        System.out.println("检查输出合法");
        return response;
    }

    private boolean containsSensitiveContent(List<Message> messages) {
        System.out.println("检查敏感词");
        return false;
    }

    @Override
    public String getName() {
        return "gi";
    }
}
