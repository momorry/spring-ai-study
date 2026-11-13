package com.moli.agent.scope.common.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 历史消息里的工具调用痕迹 VO。
 * <p>上下文中的工具结果是独立的 {@code role=TOOL} 消息（只含 ToolResultBlock），逐条映射成气泡会
 * 渲染出一个空白助手气泡，还会把前端「重新生成」用 index-1 定位用户消息的逻辑带偏。
 * 因此这类消息不单独成条，而是折叠成这里的一条记录，挂在发起调用的那条 assistant 消息上。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolCallVo {

    /**
     * 结果摘要保留的最大字数。
     * <p>实时流（{@code type=tool} 事件）与历史加载两条路径必须用同一个截断规则，否则刷新前后卡片内容会不一样。
     */
    public static final int SUMMARY_MAX_LENGTH = 200;

    /** 工具名，如 query_city_weather */
    private String toolName;

    /** 工具调用 ID，可与同一条回答里的 ToolUseBlock 对应 */
    private String toolCallId;

    /** 结果状态：success / error / denied / interrupted / running（取自 ToolResultState） */
    private String state;

    /** 结果摘要，只用于展示，不保证是完整输出（长输出会被截断） */
    private String summary;

    /**
     * 把工具输出收敛成一段可直接展示的摘要：去首尾空白，超长截断并补省略号。
     *
     * @param raw 工具输出原文，可为 null
     * @return 摘要文本，入参为空时返回空串
     */
    public static String summarize(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.strip();
        return text.length() > SUMMARY_MAX_LENGTH
                ? text.substring(0, SUMMARY_MAX_LENGTH) + "…"
                : text;
    }
}
