package com.moli.agent.scope.common.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 历史消息项 VO
 * 用于返回 AgentScope 持久化的对话历史
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistoryMessageVo {

    /**
     * 消息ID（消息的唯一标识）
     */
    private String id;

    /**
     * 消息角色：user / assistant / system / tool
     */
    private String role;

    /**
     * 消息文本内容（从 ContentBlock 中提取）
     */
    private String content;

    /**
     * 思考过程内容（如果有）
     */
    private String thinking;

    /**
     * 时间戳
     */
    private String timestamp;

    /**
     * 文件产物信息（如果有）
     */
    private FileInfo fileInfo;

    /**
     * 等待用户确认的工具调用（人在回路），只出现在最后一条 assistant 消息上。
     * <p>ASKING 状态随 AgentState 一同落库，所以刷新页面、重新打开会话后依旧能把确认卡片补回来；
     * 不补的话用户既没地方裁决，又会因为会话卡在 ASKING 而发不进任何新消息。
     */
    private List<ConfirmRequest> confirmRequests;

    /**
     * 这一轮执行过的工具调用（由上下文中 role=TOOL 的消息折叠而来），挂在发起调用的那条 assistant 消息上。
     * <p>工具结果本身不再单独成一条历史消息，否则前端会多出一个没有正文的助手气泡。
     */
    private List<ToolCallVo> toolCalls;

    /**
     * 额外元数据
     */
    private Map<String, Object> metadata;
}
