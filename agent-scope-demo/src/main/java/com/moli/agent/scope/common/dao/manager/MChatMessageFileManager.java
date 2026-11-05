package com.moli.agent.scope.common.dao.manager;

import com.baomidou.mybatisplus.spring.service.IService;
import com.moli.agent.scope.common.dao.entity.MChatMessageFile;

import java.util.List;

/**
 * <p>
 * 消息文件产物关联表 服务类
 * </p>
 *
 * @author moli
 * @since 2026-11-05
 */
public interface MChatMessageFileManager extends IService<MChatMessageFile> {

    /**
     * 保存文件产物记录。
     *
     * @param userId    用户ID
     * @param sessionId 会话ID
     * @param replyId   回复ID
     * @param fileName  文件名称
     * @param fileUrl   文件访问地址
     * @param fileSize  文件大小（字节，可为 null）
     * @param fileType  文件类型 / MIME
     * @return 保存成功后的记录ID
     */
    Long saveFileRecord(Long userId, String sessionId, String replyId, String fileName,
                        String fileUrl, Long fileSize, String fileType);

    /**
     * 根据回复ID查询文件产物。
     *
     * @param replyId 回复ID
     * @return 文件产物记录，不存在时返回 null
     */
    MChatMessageFile getByReplyId(String replyId);

    /**
     * 批量根据回复ID列表查询文件产物。
     *
     * @param replyIds 回复ID列表
     * @return 文件产物列表（按 replyId 匹配）
     */
    List<MChatMessageFile> listByReplyIds(List<String> replyIds);

    /**
     * 查询指定会话的所有文件产物（按创建时间升序）。
     *
     * @param sessionId 会话ID
     * @return 文件产物列表
     */
    List<MChatMessageFile> listBySessionId(String sessionId);
}
