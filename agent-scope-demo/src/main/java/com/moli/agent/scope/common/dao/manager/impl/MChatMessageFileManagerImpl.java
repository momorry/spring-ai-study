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
    public Long saveFileRecord(Long userId, String sessionId, String messageId, String fileName,
                               String fileUrl, Long fileSize, String fileType) {
        MChatMessageFile file = new MChatMessageFile();
        file.setUserId(userId);
        file.setSessionId(sessionId);
        file.setMessageId(messageId);
        file.setFileName(fileName);
        file.setFileUrl(fileUrl);
        file.setFileSize(fileSize);
        file.setFileType(fileType);
        save(file);
        log.info("保存文件产物记录: userId={}, sessionId={}, messageId={}, fileName={}",
                userId, sessionId, messageId, fileName);
        return file.getId();
    }

    @Override
    public MChatMessageFile getByMessageId(String messageId) {
        if (messageId == null || messageId.isBlank()) {
            return null;
        }
        return lambdaQuery()
                .eq(MChatMessageFile::getMessageId, messageId)
                .one();
    }

    @Override
    public List<MChatMessageFile> listByMessageIds(List<String> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) {
            return Collections.emptyList();
        }
        // 去重
        List<String> distinctMessageIds = messageIds.stream().distinct().collect(Collectors.toList());
        return lambdaQuery()
                .in(MChatMessageFile::getMessageId, distinctMessageIds)
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
