package com.moli.agent.scope.common.dao.manager.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.moli.agent.scope.common.dao.entity.MChatArtifact;
import com.moli.agent.scope.common.dao.manager.MChatArtifactManager;
import com.moli.agent.scope.common.dao.mapper.MChatArtifactMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * <p>
 * 对话产物台账 服务实现类
 * </p>
 *
 * @author moli
 * @since 2026-11-13
 */
@Slf4j
@Service
public class MChatArtifactManagerImpl extends ServiceImpl<MChatArtifactMapper, MChatArtifact>
        implements MChatArtifactManager {

    /**
     * 覆盖动作的操作人标记。
     * <p>链式 update 没有实体对象可交给 {@code MetaObjectHandler} 做自动填充，所以 update_by / update_time
     * 必须在这里显式 set，否则会留下空值。
     */
    private static final String SUPERSEDE_OPERATOR = "artifact-delivery";

    /** 轮末回填 message_id 的操作人标记，与交付写入区分开，便于事后排查是哪一步改的行 */
    private static final String BIND_OPERATOR = "artifact-backfill";

    @Override
    public Long saveArtifact(MChatArtifact artifact) {
        save(artifact);
        log.info("产物台账已落库: artifactId={}, userId={}, sessionId={}, messageId={}, fileName={}, objectKey={}",
                artifact.getId(), artifact.getUserId(), artifact.getSessionId(),
                artifact.getMessageId(), artifact.getFileName(), artifact.getObjectKey());
        return artifact.getId();
    }

    @Override
    public boolean hasActiveArtifact(Long userId, String sessionId, String fileName) {
        if (userId == null || sessionId == null || fileName == null) {
            return false;
        }
        return lambdaQuery()
                .eq(MChatArtifact::getUserId, userId)
                .eq(MChatArtifact::getSessionId, sessionId)
                .eq(MChatArtifact::getFileName, fileName)
                .eq(MChatArtifact::getStatus, MChatArtifact.STATUS_ACTIVE)
                .count() > 0;
    }

    @Override
    public int supersedeSameName(Long userId, String sessionId, String fileName) {
        if (userId == null || sessionId == null || fileName == null) {
            return 0;
        }
        Wrapper<MChatArtifact> wrapper = Wrappers.<MChatArtifact>lambdaUpdate()
                .eq(MChatArtifact::getUserId, userId)
                .eq(MChatArtifact::getSessionId, sessionId)
                .eq(MChatArtifact::getFileName, fileName)
                .eq(MChatArtifact::getStatus, MChatArtifact.STATUS_ACTIVE)
                .set(MChatArtifact::getStatus, MChatArtifact.STATUS_SUPERSEDED)
                .set(MChatArtifact::getUpdateBy, SUPERSEDE_OPERATOR)
                .set(MChatArtifact::getUpdateTime, LocalDateTime.now());
        int rows = baseMapper.update(null, wrapper);
        if (rows > 0) {
            log.info("同名产物已被覆盖: userId={}, sessionId={}, fileName={}, 置为 SUPERSEDED 行数={}",
                    userId, sessionId, fileName, rows);
        }
        return rows;
    }

    @Override
    public int bindMessageId(Long userId, String sessionId, String messageId, LocalDateTime since) {
        if (userId == null || sessionId == null || StringUtils.isBlank(messageId) || since == null) {
            return 0;
        }
        Wrapper<MChatArtifact> wrapper = Wrappers.<MChatArtifact>lambdaUpdate()
                .eq(MChatArtifact::getUserId, userId)
                .eq(MChatArtifact::getSessionId, sessionId)
                .ge(MChatArtifact::getCreateTime, since)
                // 只补空位：已被上一轮回填过的不能抢走，否则产物会从它真正所属的那条回答上掉下去
                .isNull(MChatArtifact::getMessageId)
                .set(MChatArtifact::getMessageId, messageId)
                .set(MChatArtifact::getUpdateBy, BIND_OPERATOR)
                .set(MChatArtifact::getUpdateTime, LocalDateTime.now());
        int rows = baseMapper.update(null, wrapper);
        if (rows > 0) {
            log.info("产物已回填消息ID: userId={}, sessionId={}, messageId={}, 回填行数={}",
                    userId, sessionId, messageId, rows);
        }
        return rows;
    }

    @Override
    public List<MChatArtifact> listSince(Long userId, String sessionId, LocalDateTime since) {
        if (userId == null || sessionId == null || since == null) {
            return Collections.emptyList();
        }
        return lambdaQuery()
                .eq(MChatArtifact::getUserId, userId)
                .eq(MChatArtifact::getSessionId, sessionId)
                .eq(MChatArtifact::getStatus, MChatArtifact.STATUS_ACTIVE)
                .ge(MChatArtifact::getCreateTime, since)
                .orderByAsc(MChatArtifact::getCreateTime)
                .orderByAsc(MChatArtifact::getId)
                .list();
    }

    @Override
    public List<MChatArtifact> listByMessageIds(List<String> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> distinctMessageIds = messageIds.stream()
                .filter(StringUtils::isNotBlank)
                .distinct()
                .toList();
        if (distinctMessageIds.isEmpty()) {
            return Collections.emptyList();
        }
        return lambdaQuery()
                .in(MChatArtifact::getMessageId, distinctMessageIds)
                .eq(MChatArtifact::getStatus, MChatArtifact.STATUS_ACTIVE)
                .orderByAsc(MChatArtifact::getCreateTime)
                .orderByAsc(MChatArtifact::getId)
                .list();
    }
}
