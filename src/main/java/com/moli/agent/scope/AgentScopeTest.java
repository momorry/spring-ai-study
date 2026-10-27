package com.moli.agent.scope;

import com.moli.agent.scope.model.Model;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.UserMessage;
import io.agentscope.harness.agent.HarnessAgent;

import java.nio.file.Path;

public class AgentScopeTest {

    public static void main(String[] args) {
        HarnessAgent agent = HarnessAgent.builder()
                .name("Assistant")
                .sysPrompt("你是一个乐于助人的 AI 助手")
                .model(Model.streamModel())
                .workspace(Path.of("./workspace"))   // 工作区目录，记忆/技能都在这
                .build();

        String reply = agent.call(
                new UserMessage("你好，请介绍一下自己"),
                RuntimeContext.empty()
        ).block().getTextContent();

        System.out.println(reply);

    }
}
