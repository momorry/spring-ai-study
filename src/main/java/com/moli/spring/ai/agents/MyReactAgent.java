package com.moli.spring.ai.agents;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.moli.spring.ai.base.ChatModelUtil;
import com.moli.spring.ai.prompt.DynamicPromptInterceptor;
import com.moli.spring.ai.tools.SearchTool;
import com.moli.spring.ai.tools.SearchToolRequest;
import com.moli.spring.ai.tools.handler.ToolErrorInterceptor;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.ArrayList;
import java.util.List;

public class MyReactAgent {

    public static ReactAgent get() {
        ToolCallback searchTool = FunctionToolCallback.builder("search", new SearchTool())
                .description("搜索工具")
                .inputType(SearchToolRequest.class)
                .build();
        List<ToolErrorInterceptor> errorInterceptors = new ArrayList<>();
        errorInterceptors.add(new ToolErrorInterceptor());
        return ReactAgent.builder().model(ChatModelUtil.getModel())
                .name("my_agent")
                .tools(searchTool)
                .interceptors(errorInterceptors)
                .systemPrompt("你是一个专业的技术助手。请准确、简洁地回答问题。")
                .build();
    }

    public static ReactAgent getByIntro() {
        String instruction = """
                你是一个经验丰富的软件架构师。
                
                在回答问题时，请：
                1. 首先理解用户的核心需求
                2. 分析可能的技术方案
                3. 提供清晰的建议和理由
                4. 如果需要更多信息，主动询问
                
                保持专业、友好的语气。
                """;
        ToolCallback searchTool = FunctionToolCallback.builder("search", new SearchTool())
                .description("搜索工具")
                .inputType(SearchToolRequest.class)
                .build();
        List<ToolErrorInterceptor> errorInterceptors = new ArrayList<>();
        errorInterceptors.add(new ToolErrorInterceptor());
        return ReactAgent.builder().model(ChatModelUtil.getModel())
                .name("architect_agent")
                .tools(searchTool)
                .interceptors(errorInterceptors).instruction(instruction)
                .build();
    }

    public static ReactAgent getByDynamicMpt() {
        String instruction = """
                你是一个经验丰富的软件架构师。
                
                在回答问题时，请：
                1. 首先理解用户的核心需求
                2. 分析可能的技术方案
                3. 提供清晰的建议和理由
                4. 如果需要更多信息，主动询问
                
                保持专业、友好的语气。
                """;
        ToolCallback searchTool = FunctionToolCallback.builder("search", new SearchTool())
                .description("搜索工具")
                .inputType(SearchToolRequest.class)
                .build();
        return ReactAgent.builder().model(ChatModelUtil.getModel())
                .name("architect_agent")
                .tools(searchTool).instruction(instruction)
                .interceptors(new ToolErrorInterceptor(), new DynamicPromptInterceptor())
                .build();
    }

}
