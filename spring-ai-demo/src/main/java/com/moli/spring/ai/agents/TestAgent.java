package com.moli.spring.ai.agents;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;

public class TestAgent {

    public static void main(String[] args) throws GraphRunnerException {

        ReactAgent reactAgent = MyReactAgent.get();
// 字符串输入
        AssistantMessage response1 = reactAgent.call("杭州的天气怎么样？");
        System.out.println(response1.getText());

// UserMessage 输入
        ReactAgent byIntro = MyReactAgent.getByIntro();
        UserMessage userMessage = new UserMessage("帮我分析这个问题");
        AssistantMessage response2 = byIntro.call(userMessage);
        System.out.println(response2.getText());

// 多个消息
        ReactAgent byDynamicMpt = MyReactAgent.getByDynamicMpt();
        List<Message> messages = List.of(
                new UserMessage("我想了解 Java 多线程"),
                new UserMessage("特别是线程池的使用")
        );
        AssistantMessage response3 = byDynamicMpt.call(messages);
        System.out.println(response3.getText());

    }
}
