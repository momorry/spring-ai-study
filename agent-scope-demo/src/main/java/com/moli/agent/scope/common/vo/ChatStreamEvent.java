package com.moli.agent.scope.common.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 流式对话事件 DTO（防腐层）。
 * <p>包装 AgentScope SDK 的 {@code AgentEvent}，向前端输出稳定的自定义协议，
 * 同时支持携带业务扩展字段（如文件产物、token 用量等）。
 *
 * <h3>事件类型（type）约定</h3>
 * <ul>
 *   <li>{@code thinking} — 推理阶段增量文本（对应 SDK THINKING_BLOCK_DELTA）</li>
 *   <li>{@code text} — 回答阶段增量文本（对应 SDK TEXT_BLOCK_DELTA）</li>
 *   <li>{@code result} — 最终聚合结果（对应 SDK AGENT_RESULT）</li>
 *   <li>{@code file} — 文件产物已生成（对应 SDK CustomEvent "file_generated"）</li>
 *   <li>{@code done} — 本轮对话全部完成</li>
 *   <li>{@code error} — 发生错误</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatStreamEvent {

    /**
     * 会话ID
     */
    private String sessionId;

    /** 事件类型，见类注释 */
    private String type;

    /**
     * 内容块ID。
     * <p>由 SDK 在每次开始一个新的文本/思考块时生成，同一个块内的增量事件共享相同的 blockId，
     * 前端可据此拼接完整内容、区分不同块。
     */
    private String blockId;

    /**
     * 回复轮次ID。
     * <p>标识一次完整的模型回复（一轮 assistant reply），同一轮内的所有增量事件共享相同的 replyId，
     * 前端可据此将 delta 归属到正确的回复轮次。
     */
    private String replyId;

    /** 增量文本（thinking / text 阶段） */
    private String delta;

    /** 完整文本内容（result 阶段） */
    private String content;

    /** 推理过程（result 阶段） */
    private String thinking;

    /** 文件产物信息（file 阶段） */
    private FileInfo fileInfo;

    /** 通用扩展字段（token 用量、耗时统计等） */
    private Map<String, Object> metadata;

    /** 错误信息（error 阶段） */
    private String errorMessage;
}
