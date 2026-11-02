package com.moli.spring.ai.tools;

// 搜索工具入参：必须是一个对象，function calling 要求参数 schema 为 type: "object"
public record SearchToolRequest(String query) {
}