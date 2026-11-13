package com.moli.agent.scope.common.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户对单个待确认工具调用的裁决（前端 → 服务端）。
 * <p>服务端会拿 {@link #toolCallId} 到会话上下文里找回那条 {@code ASKING} 的
 * {@code ToolUseBlock}，组装成 SDK 的 {@code ConfirmResult} 随下一次调用下发，
 * 由框架完成「允许 → 提升为 ALLOWED 并执行」或「拒绝 → 写入 DENIED 工具结果」。
 * <p>入参一律以服务端上下文里的原始值为准，不接受前端回传，避免确认通道被改成另一个命令。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmDecision {

    /** 要裁决的工具调用 ID，来自 {@link ConfirmRequest#getToolCallId()} */
    private String toolCallId;

    /** true = 允许执行；false = 拒绝 */
    private boolean confirmed;

    /** 拒绝原因（可选）：非空时作为 DENIED 工具结果的正文写回模型上下文 */
    private String reason;
}
