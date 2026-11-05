package com.moli.agent.scope.controller;

import com.moli.agent.scope.common.base.ApiResult;
import com.moli.agent.scope.common.base.SessionBase;
import com.moli.agent.scope.common.security.UserContext;
import com.moli.agent.scope.common.vo.ChatStreamEvent;
import com.moli.agent.scope.common.vo.HistoryMessageVo;
import com.moli.agent.scope.common.vo.InConversationVo;
import com.moli.agent.scope.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

@RequiredArgsConstructor
@RestController
public class ChatController {


    private final ChatService chatService;

    @PostMapping(value = "/reactagent/conversation", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ChatStreamEvent> conversation(@RequestBody InConversationVo conversationVo) {
        // 方案1：在当前请求线程（servlet 线程）取出 JWT 认证身份，覆盖前端传入的 userId，
        // 之后随 VO 显式向下传递；异步执行（boundedElastic）中不再依赖 ThreadLocal。
        conversationVo.setUserId(UserContext.getUserId());
        return chatService.conversation(conversationVo);
    }

    @PostMapping("/reactagent/conversation/stop")
    public ApiResult<Void> stop(@RequestBody SessionBase sessionBase) {
        // 同理：以 JWT 身份为准，避免越权中断他人会话
        sessionBase.setUserId(UserContext.getUserId());
        return chatService.conversationStop(sessionBase);
    }

    /**
     * 加载会话历史消息
     *
     * @param sessionId 会话ID
     * @return 历史消息列表
     */
    @GetMapping("/reactagent/conversation/history")
    public ApiResult<List<HistoryMessageVo>> loadHistory(@RequestParam("sessionId") String sessionId) {
        // 使用 JWT 认证的用户ID
        Long userId = UserContext.getUserId();
        return chatService.loadHistory(userId.toString(), sessionId);
    }

}
