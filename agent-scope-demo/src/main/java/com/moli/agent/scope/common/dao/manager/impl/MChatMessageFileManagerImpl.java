package com.moli.agent.scope.common.dao.manager.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.moli.agent.scope.common.dao.entity.MChatMessageFile;
import com.moli.agent.scope.common.dao.mapper.MChatMessageFileMapper;
import com.moli.agent.scope.common.dao.manager.MChatMessageFileManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <p>
 * 消息文件产物关联表 服务实现类
 * </p>
 *
 * @author moli
 * @since 2026-11-05
 */
@Slf4j
@Service
public class MChatMessageFileManagerImpl extends ServiceImpl<MChatMessageFileMapper, MChatMessageFile>
        implements MChatMessageFileManager {

    @Override
    public Long saveFileRecord(Long userId, String sessionId, String replyId, String fileName,
                               String fileUrl, Long fileSize, String fileType) {
        MChatMessageFile file = new MChatMessageFile();
        file.setUserId(userId);
        file.setSessionId(sessionId);
        file.setReplyId(replyId);
        file.setFileName(fileName);
        file.setFileUrl(fileUrl);
        file.setFileSize(fileSize);
        file.setFileType(fileType);
        save(file);
        log.info("保存文件产物记录: userId={}, sessionId={}, replyId={}, fileName={}",
                userId, sessionId, replyId, fileName);
        return file.getId();
    }

    @Override
    public MChatMessageFile getByReplyId(String replyId) {
        if (replyId == null || replyId.isBlank()) {
            return null;
        }
        return lambdaQuery()
                .eq(MChatMessageFile::getReplyId, replyId)
                .one();
    }

    @Override
    public List<MChatMessageFile> listByReplyIds(List<String> replyIds) {
        if (replyIds == null || replyIds.isEmpty()) {
            return Collections.emptyList();
        }
        // 去重
        List<String> distinctReplyIds = replyIds.stream().distinct().collect(Collectors.toList());
        return lambdaQuery()
                .in(MChatMessageFile::getReplyId, distinctReplyIds)
                .list();
    }

    @Override
    public List<MChatMessageFile> listBySessionId(String sessionId) {
        return lambdaQuery()
                .eq(MChatMessageFile::getSessionId, sessionId)
                .orderByAsc(MChatMessageFile::getCreateTime)
                .list();
    }
}
