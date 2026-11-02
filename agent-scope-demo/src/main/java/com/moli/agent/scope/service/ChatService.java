package com.moli.agent.scope.service;

import com.moli.agent.scope.common.vo.InConversationVo;
import com.moli.agent.scope.harness.agent.SimpleReActAgent;
import io.agentscope.core.event.AgentEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@RequiredArgsConstructor
@Service
public class ChatService {

    private final SimpleReActAgent simpleReActAgent;
    public Flux<AgentEvent> conversation(InConversationVo conversationVo) {
        return simpleReActAgent.conv(conversationVo);
    }
}
