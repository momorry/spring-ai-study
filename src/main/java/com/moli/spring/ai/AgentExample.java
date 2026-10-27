package com.moli.spring.ai;

import com.moli.spring.ai.base.ChatModelUtil;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class AgentExample {


    public static void main(String[] args) throws Exception {

        ChatModel chatModel = ChatModelUtil.getModel();
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage("你是一个友好、耐心的 AI 学习助手，请使用中文回答，并尽量给出清晰的示例。"));

        System.out.println("Spring AI OpenAI 基础对话示例已启动。");
        System.out.println("输入问题开始对话，输入 exit 或 quit 结束。");

        try (Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8)) {
            while (true) {
                System.out.print("\n你：");
                String userInput = scanner.nextLine().trim();

                if (userInput.isBlank()) {
                    continue;
                }
                if ("exit".equalsIgnoreCase(userInput) || "quit".equalsIgnoreCase(userInput)) {
                    System.out.println("对话结束。");
                    break;
                }

                messages.add(new UserMessage(userInput));
                ChatResponse response = chatModel.call(new Prompt(messages));
                String assistantContent = response.getResult().getOutput().getText();

                messages.add(new AssistantMessage(assistantContent));
                System.out.println("AI：" + assistantContent);
            }
        }
    }


}