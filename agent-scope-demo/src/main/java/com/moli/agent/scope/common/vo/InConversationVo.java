package com.moli.agent.scope.common.vo;

import com.moli.agent.scope.common.base.SessionBase;
import lombok.Data;
import org.jetbrains.annotations.NotNull;

@Data
public class InConversationVo {

    private String sessionId;

    @NotNull
    private Long userId;

    private String message;

    /**
     * 是否为“重新生成”（重放上一条用户消息）。
     * <p>为 true 时服务端会先把会话上下文回滚到该条用户消息之前（删掉它及其产生的
     * ASSISTANT / TOOL 消息），再重放本次消息；同时不再累加会话轮数。
     * <p>不传该标志时，同一条消息会被当成新一轮追加，导致上下文里出现重复输入。
     */
    private boolean regenerate;

    /**
     * 本次要重放的那条用户消息 ID（即上下文里 {@code Msg.getId()}），回滚点按它精确定位。
     * <p>来源：历史会话接口每条消息本来就带 id；实时对话由服务端在 DONE 事件里回传本轮
     * 用户消息 id，前端记下后原样带回。
     * <p>为空或在上下文里定位不到时，服务端直接抛异常中止本次重新生成（不做任何退回猜测），
     * 避免把会话上下文静默改脏。
     */
    private String regenerateMessageId;

    /**
     * 本次对话使用的模型标识（前端下拉选择，对应 m_llm_model_config.model_name）。
     * 为空时服务端回退 application-local.yml 的 llm.model 默认模型。
     */
    private String modelName;
}
