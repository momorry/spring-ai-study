package com.moli.agent.scope.common.vo;

import com.moli.agent.scope.common.enums.MyAgentEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
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

    /**
     * 本轮新交付的产物（file 阶段）。
     * <p>一次 {@code deliver_artifact} 就可能出一个，一轮里模型可以连交好几个（报告 + 图表 + 归档），
     * 所以是列表；与历史加载侧的 {@code HistoryMessageVo#artifacts} 是同一份结构，刷新前后才对得上。
     */
    private List<FileInfo> artifacts;

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

    /**
     * 待用户确认的工具调用（confirm 阶段）。
     * <p>Agent 命中 ASK 权限规则后会暂停本轮并下发该事件，前端渲染确认卡片；
     * 用户裁决后把 {@link ConfirmRequest#getToolCallId()} 原样带回（见 {@link ConfirmDecision}），
     * 会话才能继续。不响应这条事件，会话会一直停在 ASKING 状态。
     */
    private List<ConfirmRequest> confirmRequests;

    /**
     * 刚执行完毕的一次工具调用（tool 阶段）。
     * <p>与历史加载时 {@code HistoryMessageVo#getToolCalls()} 是同一份展示信息：实时流下不发这条事件的话，
     * 只有刷新页面才能看到本轮调过哪些工具，流式中与刷新后的视图会对不上。
     */
    private List<ToolCallVo> toolCalls;
}
