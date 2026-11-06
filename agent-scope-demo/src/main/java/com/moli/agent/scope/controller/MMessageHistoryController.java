package com.moli.agent.scope.controller;

import com.moli.agent.scope.common.base.ApiResult;
import com.moli.agent.scope.common.security.UserContext;
import com.moli.agent.scope.common.vo.HistoryMessageVo;
import com.moli.agent.scope.service.MessageHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
public class MMessageHistoryController {


    private final MessageHistoryService messageHistoryService;

    /**
     * 加载会话历史消息
     *
     * @param sessionId 会话ID
     * @return 历史消息列表
     */
    @GetMapping("/conversation/message/history")
    public ApiResult<List<HistoryMessageVo>> loadHistory(@RequestParam("sessionId") String sessionId) {
        Long userId = UserContext.getUserId();
        return ApiResult.ok(messageHistoryService.loadHistory(userId.toString(), sessionId));
    }

}
