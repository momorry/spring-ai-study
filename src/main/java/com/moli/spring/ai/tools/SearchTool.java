package com.moli.spring.ai.tools;

import org.springframework.ai.chat.model.ToolContext;

import java.util.function.BiFunction;

// 定义工具（示例：仅一个搜索工具）
public class SearchTool implements BiFunction<SearchToolRequest, ToolContext, String> {
    @Override
    public String apply(SearchToolRequest query, ToolContext context) {
// 实现搜索逻辑
        return "搜索结果: " + query;
    }
}
