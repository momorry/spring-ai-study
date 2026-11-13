package com.moli.agent.scope.common.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 待用户确认的工具调用（人在回路下发给前端的展示载荷）。
 * <p>来源是 SDK 的 {@code RequireUserConfirmEvent#getToolCalls()}：框架把这一批调用标成
 * {@code ASKING} 写入会话上下文后暂停本轮，前端据此渲染确认卡片。
 * <p>{@code toolCallId} 是确认恢复时的唯一凭据，必须原样带回（{@link ConfirmDecision#getToolCallId()}），
 * 服务端据此在上下文里找到那条 ASKING 调用；对不上框架会直接拒绝本次恢复请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmRequest {

    /** 工具调用 ID（SDK ToolUseBlock.getId()），恢复时按它定位 ASKING 调用 */
    private String toolCallId;

    /** 工具名，如 read_path_file_names / execute_shell_command */
    private String toolName;

    /** 模型给该工具的入参原文，前端可自行格式化展示 */
    private Map<String, Object> arguments;

    /** 入参的单行摘要（命令文本 / 目录路径等），省掉前端的取值判断 */
    private String argumentSummary;
}
