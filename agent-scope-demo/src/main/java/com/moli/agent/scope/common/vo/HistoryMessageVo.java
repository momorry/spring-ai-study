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
     * 消息角色：user / assistant / system / tool
     */
    private String role;

    /**
     * 回复ID（标识一次完整的模型回复）
     */
    private String replyId;

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
     * 额外元数据
     */
    private Map<String, Object> metadata;
}
