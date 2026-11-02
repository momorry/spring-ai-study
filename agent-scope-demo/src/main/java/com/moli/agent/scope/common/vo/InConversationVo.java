package com.moli.agent.scope.common.vo;

import com.moli.agent.scope.common.base.SessionBase;
import lombok.Data;

@Data
public class InConversationVo {

    private String sessionId;
    private Long userId;

    private String message;
}
