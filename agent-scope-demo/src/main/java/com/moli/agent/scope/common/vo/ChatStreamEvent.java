package com.moli.agent.scope.common.vo;

import com.moli.agent.scope.common.enums.MyAgentEvent;
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
 * <p>事件类型定义见 {@link MyAgentEvent}。
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

    /** 事件类型，见 {@link MyAgentEvent} */
    private MyAgentEvent type;

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

    /**
     * 本轮用户消息 ID（done 阶段携带）。
     * <p>前端需记下并归到这条回答上，点“重新生成”时作为 regenerateMessageId 回传，
     * 服务端据此精确定位上下文回滚点（实时会话里前端拿不到这个 id，只能靠这里下发）。
     */
    private String userMessageId;
}
