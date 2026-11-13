package com.moli.agent.scope.common.vo;

import com.moli.agent.scope.common.dao.entity.MChatArtifact;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 产物信息（前端展示用的投影）。
 * <p>由 m_chat_artifact 查出来后拼装：流式下发的 FILE 事件与历史加载回来的是同一份结构，
 * 保证刷新前后看到的不是一个东西。
 *
 * <p>刻意不带存储位置信息（objectKey、bucket、预签名 URL 一概不下发）：前端只需要
 * {@link #artifactId}，下载时凭它调 {@code /artifact/download}，由服务端校验归属后再取对象。
 *
 * @author moli
 * @since 2026-11-05
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileInfo {

    /** 产物ID（m_chat_artifact 主键），下载接口的唯一入参 */
    private Long artifactId;

    /** 文件名称（含扩展名），如 "pixel-jump.html" */
    private String fileName;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 文件类型 / MIME，如 "text/html"、"application/pdf" */
    private String fileType;

    /** 模型填写的产物说明，可空 */
    private String description;

    /** 所属消息ID，供前端把产物归到那条回答上 */
    private String messageId;

    /**
     * 台账行 → 展示投影，唯一的一处转换。
     * <p>流式卡片与历史加载都必须走这里：两处各写一遍的话，总有一处会漏字段，
     * 用户刷新前后看到的产物就不是一回事了。
     */
    public static FileInfo from(MChatArtifact artifact) {
        if (artifact == null) {
            return null;
        }
        return FileInfo.builder()
                .artifactId(artifact.getId())
                .fileName(artifact.getFileName())
                .fileSize(artifact.getFileSize())
                .fileType(artifact.getContentType())
                .description(artifact.getDescription())
                .messageId(artifact.getMessageId())
                .build();
    }
}
