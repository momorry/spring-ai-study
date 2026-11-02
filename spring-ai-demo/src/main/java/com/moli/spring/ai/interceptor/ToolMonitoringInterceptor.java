package com.moli.spring.ai.interceptor;

import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallResponse;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolInterceptor;


public class ToolMonitoringInterceptor extends ToolInterceptor {
    @Override
    public ToolCallResponse interceptToolCall(ToolCallRequest request, ToolCallHandler handler) {
        long startTime = System.currentTimeMillis();
        try {
            ToolCallResponse response = handler.call(request);
            logSuccess(request, System.currentTimeMillis() - startTime);
            return response;
        } catch (Exception e) {
            logError(request, e, System.currentTimeMillis() - startTime);
            return ToolCallResponse.error(request.getToolCallId(), request.getToolName(),
                    "工具执行遇到问题，请稍后重试");
        }
    }

    private void logError(ToolCallRequest request, Exception e, long l) {

    }

    private void logSuccess(ToolCallRequest request, long l) {

    }

    @Override
    public String getName() {
        return "tmi";
    }
}
