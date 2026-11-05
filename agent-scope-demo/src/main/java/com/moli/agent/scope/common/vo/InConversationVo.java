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
}
