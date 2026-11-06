package com.moli.agent.scope.controller;

import com.moli.agent.scope.common.base.ApiResult;
import com.moli.agent.scope.common.dao.entity.MChatSession;
import com.moli.agent.scope.common.dao.manager.MChatSessionManager;
import com.moli.agent.scope.common.security.UserContext;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * <p>
 * 聊天会话表（历史会话列表） 前端控制器
 * </p>
 * 用户身份一律以 JWT 认证结果（{@link UserContext}）为准，避免越权访问他人会话。
 *
 * @author moli
 * @since 2026-11-03
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/mChatSession")
public class MChatSessionController {

    private final MChatSessionManager chatSessionManager;

    /**
     * 查询当前登录用户的历史会话列表（置顶优先，其次按更新时间倒序）。
     */
    @GetMapping("/list")
    public ApiResult<List<MChatSession>> list() {
        Long userId = UserContext.getUserId();
        return ApiResult.ok(chatSessionManager.listByUser(userId));
    }

    /**
     * 分页查询当前登录用户的历史会话列表（置顶优先，其次按更新时间倒序）。
     * 供前端无限滚动加载：逐页递增 pageNum，直到 records 为空或已达 total。
     *
     * @param pageNum  页码（从 1 开始）
     * @param pageSize 每页条数
     */
    @GetMapping("/page")
    public ApiResult<Page<MChatSession>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        Long userId = UserContext.getUserId();
        return ApiResult.ok(chatSessionManager.pageByUser(userId, pageNum, pageSize));
    }

    /**
     * 重命名会话标题。仅允许操作本人会话。
     */
    @PutMapping("/rename")
    public ApiResult<Void> rename(@RequestParam("id") Long id,
                                  @RequestParam("title") String title) {
        MChatSession owned = loadOwned(id);
        if (owned == null) {
            return ApiResult.fail("会话不存在或无权操作");
        }
        owned.setTitle(title);
        chatSessionManager.updateById(owned);
        return ApiResult.ok();
    }

    /**
     * 删除会话（逻辑删除）。仅允许操作本人会话。
     *
     */
    @DeleteMapping("/delete/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        MChatSession owned = loadOwned(id);
        if (owned == null) {
            return ApiResult.fail("会话不存在或无权操作");
        }
        chatSessionManager.removeById(owned.getId());
        return ApiResult.ok();
    }

    /**
     * 按主键加载会话，并校验归属当前登录用户；不属于当前用户或不存在时返回 null。
     */
    private MChatSession loadOwned(Long id) {
        if (id == null) {
            return null;
        }
        Long userId = UserContext.getUserId();
        MChatSession session = chatSessionManager.getById(id);
        if (session == null || userId == null || !userId.equals(session.getUserId())) {
            return null;
        }
        return session;
    }
}
