package com.moli.spring.ai.hooks;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.AgentHook;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class LogHook extends AgentHook {
    @Override
    public String getName() {
        return "logging";
    }

    @Override
    public CompletableFuture<Map<String, Object>> beforeAgent(OverAllState state, RunnableConfig config) {
        System.out.println("智能体开始执行之前");
        return CompletableFuture.completedFuture(state.data());
    }
    @Override
    public CompletableFuture<Map<String, Object>> afterAgent(OverAllState state, RunnableConfig config) {
        System.out.println("智能体执行之后");
        return CompletableFuture.completedFuture(state.data());
    }

}
