package com.moli.agent.scope.common.dao.manager;

import com.moli.agent.scope.common.dao.entity.MChatSession;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.IService;

import java.util.List;

/**
 * <p>
 * 聊天会话表（历史会话列表） 服务类
 * </p>
 *
 * @author moli
 * @since 2026-11-03
 */
public interface MChatSessionManager extends IService<MChatSession> {

    /**
     * 查询指定用户的历史会话列表（置顶优先，其次按更新时间倒序）。
     *
     * @param userId 用户ID
     * @return 会话列表
     */
    List<MChatSession> listByUser(Long userId);

    /**
     * 分页查询指定用户的历史会话列表（置顶优先，其次按更新时间倒序）。
     * 使用 MyBatis-Plus 分页（依赖 PaginationInnerInterceptor）。
     *
     * @param userId   用户ID
     * @param pageNum  页码（从 1 开始）
     * @param pageSize 每页条数
     * @return 分页结果
     */
    Page<MChatSession> pageByUser(Long userId, long pageNum, long pageSize);

    /**
     * 用户发起一次对话时更新会话元数据：
     * 会话不存在则新建（标题取首条用户消息截断），存在则刷新最后消息预览、累加对话轮数。
     *
     * @param userId      用户ID
     * @param sessionId   AgentScope 会话ID
     * @param userMessage 本次用户消息
     */
    String touchOnUserMessage(Long userId, String sessionId, String userMessage);
}
