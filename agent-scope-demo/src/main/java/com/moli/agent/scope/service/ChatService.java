package com.moli.agent.scope.service;

import com.moli.agent.scope.common.base.ApiResult;
import com.moli.agent.scope.common.base.SessionBase;
import com.moli.agent.scope.common.vo.ChatStreamEvent;
import com.moli.agent.scope.common.vo.InConversationVo;
import com.moli.agent.scope.harness.agent.SimpleHarnessActAgent;
//import com.moli.agent.scope.harness.agent.SimpleReActAgent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

@RequiredArgsConstructor
@Service
public class ChatService {

//    private final SimpleReActAgent simpleReActAgent;
    private final SimpleHarnessActAgent simpleHarnessActAgent;

    public Flux<ChatStreamEvent> conversation(InConversationVo conversationVo) {
        return simpleHarnessActAgent.conversation(conversationVo);
    }

    public ApiResult<Void> conversationStop(SessionBase sessionBase) {
        simpleHarnessActAgent.convStop(sessionBase);
        return ApiResult.ok();
    }

}
