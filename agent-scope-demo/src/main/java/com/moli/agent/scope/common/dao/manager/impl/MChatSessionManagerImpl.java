package com.moli.agent.scope.common.dao.manager.impl;

import com.moli.agent.scope.common.dao.entity.MChatSession;
import com.moli.agent.scope.common.dao.mapper.MChatSessionMapper;
import com.moli.agent.scope.common.dao.manager.MChatSessionManager;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * <p>
 * 聊天会话表（历史会话列表） 服务实现类
 * </p>
 *
 * @author moli
 * @since 2026-11-03
 */
@Slf4j
@Service
public class MChatSessionManagerImpl extends ServiceImpl<MChatSessionMapper, MChatSession> implements MChatSessionManager {

    /** 标题最大长度（对应 varchar(128)） */
    private static final int TITLE_MAX_LEN = 128;
    /** 最后消息预览最大长度（对应 varchar(512)） */
    private static final int LAST_MESSAGE_MAX_LEN = 512;
    /** 用户消息为空时的兜底标题 */
    private static final String DEFAULT_TITLE = "新会话";

    @Override
    public List<MChatSession> listByUser(Long userId) {
        return lambdaQuery()
                .eq(MChatSession::getUserId, userId)
                .orderByDesc(MChatSession::getPinned)
                .orderByDesc(MChatSession::getUpdateTime)
                .list();
    }

    @Override
    public Page<MChatSession> pageByUser(Long userId, long pageNum, long pageSize) {
        Page<MChatSession> page = new Page<>(pageNum, pageSize);
        return lambdaQuery()
                .eq(MChatSession::getUserId, userId)
                .orderByDesc(MChatSession::getPinned)
                .orderByDesc(MChatSession::getUpdateTime)
                .page(page);
    }

    @Override
    public String touchOnUserMessage(Long userId, String sessionId, String userMessage, boolean regenerate) {
        if (userId == null) {
            return null;
        }
        if(StringUtils.isBlank(sessionId)) {
            sessionId = UUID.randomUUID().toString();
        }

        MChatSession existing = lambdaQuery()
                .eq(MChatSession::getUserId, userId)
                .eq(MChatSession::getSessionId, sessionId)
                .one();
        if (existing == null) {
            MChatSession session = new MChatSession();
            session.setUserId(userId);
            session.setSessionId(sessionId);
            session.setTitle(truncate(userMessage, TITLE_MAX_LEN, DEFAULT_TITLE));
            session.setLastMessage(truncate(userMessage, LAST_MESSAGE_MAX_LEN, null));
            session.setRoundCount(1);
            session.setPinned(false);
            save(session);
            log.info("新建历史会话记录: userId={}, sessionId={}", userId, sessionId);
        } else {
            existing.setLastMessage(truncate(userMessage, LAST_MESSAGE_MAX_LEN, existing.getLastMessage()));
            // 重新生成只是把同一轮重放（上下文也会先回滚），不新增轮次，轮数不能再 +1
            if (!regenerate) {
                existing.setRoundCount((existing.getRoundCount() == null ? 0 : existing.getRoundCount()) + 1);
            }
            updateById(existing);
        }
        return sessionId;
    }

    /**
     * 截断字符串到指定长度；内容为空时返回兜底值。
     */
    private String truncate(String text, int maxLen, String fallback) {
        if (text == null || text.isBlank()) {
            return fallback;
        }
        String trimmed = text.trim();
        return trimmed.length() <= maxLen ? trimmed : trimmed.substring(0, maxLen);
    }
}
