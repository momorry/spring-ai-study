package com.moli.agent.scope.controller;

import com.moli.agent.scope.common.vo.InConversationVo;
import com.moli.agent.scope.service.ChatService;
import io.agentscope.core.event.AgentEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RequiredArgsConstructor
@RestController
public class ChatController {


    private final ChatService chatService;

    @PostMapping(value = "/reactagent/conversation", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<AgentEvent> conversation(@RequestBody InConversationVo conversationVo) {
        return chatService.conversation(conversationVo);

    }
}
