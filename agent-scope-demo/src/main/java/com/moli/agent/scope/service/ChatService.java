package com.moli.agent.scope.service;

import com.moli.agent.scope.common.base.ApiResult;
import com.moli.agent.scope.common.base.SessionBase;
import com.moli.agent.scope.common.vo.ChatStreamEvent;
import com.moli.agent.scope.common.vo.HistoryMessageVo;
import com.moli.agent.scope.common.vo.InConversationVo;
import com.moli.agent.scope.harness.agent.SimpleReActAgent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

@RequiredArgsConstructor
@Service
public class ChatService {

    private final SimpleReActAgent simpleReActAgent;
    
    public Flux<ChatStreamEvent> conversation(InConversationVo conversationVo) {
        return simpleReActAgent.conversation(conversationVo);
    }

    public ApiResult<Void> conversationStop(SessionBase sessionBase) {
        simpleReActAgent.convStop(sessionBase);
        return ApiResult.ok();
    }

    /**
     * 加载会话历史消息
     *
     * @param userId    用户ID
     * @param sessionId 会话ID
     * @return 历史消息列表
     */
    public ApiResult<List<HistoryMessageVo>> loadHistory(String userId, String sessionId) {
        List<HistoryMessageVo> history = simpleReActAgent.loadHistory(userId, sessionId);
        return ApiResult.ok(history);
    }
}
