package com.moli.spring.ai.hooks;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.alibaba.cloud.ai.graph.agent.hook.messages.AgentCommand;
import com.alibaba.cloud.ai.graph.agent.hook.messages.MessagesModelHook;
import com.alibaba.cloud.ai.graph.agent.hook.messages.UpdatePolicy;
import org.springframework.ai.chat.messages.Message;

import java.util.List;

@HookPositions({HookPosition.BEFORE_MODEL,HookPosition.AFTER_MODEL})
public class MessageTrimmingHook extends MessagesModelHook {

    private static final int MAX_MESSAGES = 10;

    @Override
    public String getName() {
        return "mth";
    }

    @Override
    public AgentCommand beforeModel(List<Message> previousMessages, RunnableConfig config) {
        System.out.println("调用模型前");
        if (previousMessages.size() > MAX_MESSAGES) {
            // 只保留最后 MAX_MESSAGES 条消息
            List<Message> trimmedMessages = previousMessages.subList(
                    previousMessages.size() - MAX_MESSAGES,
                    previousMessages.size()
            );
            return new AgentCommand(trimmedMessages, UpdatePolicy.REPLACE);
        }
        // 消息数量未超过限制，直接返回原消息列表
        return new AgentCommand(previousMessages);
    }

    @Override
    public AgentCommand afterModel(List<Message> previousMessages, RunnableConfig config) {
        System.out.println("调用模型后");
        return new AgentCommand(previousMessages);
    }
}
