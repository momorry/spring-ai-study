package com.moli.agent.scope.common.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import com.moli.agent.scope.common.vo.ChatStreamEvent;

/**
 * 流式对话事件类型枚举。
 * <p>定义 {@link ChatStreamEvent} 的 {@code type} 字段取值，
 * 与 AgentScope SDK 事件类型的映射关系：
 * <ul>
 *   <li>{@link #THINKING} — 推理阶段增量文本（对应 SDK THINKING_BLOCK_DELTA）</li>
 *   <li>{@link #TEXT} — 回答阶段增量文本（对应 SDK TEXT_BLOCK_DELTA）</li>
 *   <li>{@link #RESULT} — 最终聚合结果（对应 SDK AGENT_RESULT）</li>
 *   <li>{@link #FILE} — 文件产物已生成（对应 SDK CustomEvent "file_generated"）</li>
 *   <li>{@link #CONFIRM} — 工具调用等待用户确认，本轮已暂停（对应 SDK REQUIRE_USER_CONFIRM）</li>
 *   <li>{@link #TOOL} — 一次工具调用执行完毕（对应 SDK TOOL_RESULT_END），前端渲染成工具调用痕迹</li>
 *   <li>{@link #DONE} — 本轮对话全部完成</li>
 *   <li>{@link #ERROR} — 发生错误</li>
 *   <li>{@link #OTHER} — 未识别的 SDK 事件，透传原始类型信息</li>
 * </ul>
 */
public enum MyAgentEvent {

    THINKING("thinking"),
    TEXT("text"),
    RESULT("result"),
    FILE("file"),
    CONFIRM("confirm"),
    TOOL("tool"),
    DONE("done"),
    ERROR("error"),
    OTHER("other");

    private final String value;

    MyAgentEvent(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
