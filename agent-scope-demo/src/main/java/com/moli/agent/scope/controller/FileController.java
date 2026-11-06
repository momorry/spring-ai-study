package com.moli.agent.scope.controller;

import com.moli.agent.scope.common.dao.entity.MChatMessageFile;
import com.moli.agent.scope.common.dao.manager.MChatMessageFileManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 文件产物控制器
 * 提供文件产物的下载能力
 *
 * @author moli
 * @since 2026-11-05
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/file")
public class FileController {

    private final MChatMessageFileManager messageFileManager;

    /**
     * 根据消息ID下载文件产物
     *
     * @param messageId 消息ID
     * @return 文件资源
     */
    @GetMapping("/download")
    public ResponseEntity<Resource> downloadByMessageId(@RequestParam("messageId") String messageId) {
        if (messageId == null || messageId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        // 根据 messageId 查询文件记录
        MChatMessageFile fileRecord = messageFileManager.getByMessageId(messageId);
        if (fileRecord == null) {
            log.warn("文件记录不存在: messageId={}", messageId);
            return ResponseEntity.notFound().build();
        }

        // 获取文件路径
        String fileUrl = fileRecord.getFileUrl();
        Path filePath = Paths.get(fileUrl);

        // 检查文件是否存在
        if (!Files.exists(filePath)) {
            log.error("文件不存在: filePath={}", filePath);
            return ResponseEntity.notFound().build();
        }

        try {
            // 构建文件名（处理中文编码）
            String fileName = fileRecord.getFileName();
            String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");

            // 获取文件类型
            String contentType = fileRecord.getFileType();
            if (contentType == null || contentType.isBlank()) {
                contentType = "application/octet-stream";
            }

            // 构建响应
            Resource resource = new FileSystemResource(filePath);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename*=UTF-8''" + encodedFileName)
                    .body(resource);

        } catch (Exception e) {
            log.error("下载文件失败: messageId={}, error={}", messageId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 根据消息ID获取文件信息
     *
     * @param messageId 消息ID
     * @return 文件信息
     */
    @GetMapping("/info")
    public ResponseEntity<MChatMessageFile> getFileInfo(@RequestParam("messageId") String messageId) {
        if (messageId == null || messageId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        MChatMessageFile fileRecord = messageFileManager.getByMessageId(messageId);
        if (fileRecord == null) {
            return ResponseEntity.notFound().build();
        }

        // 清除敏感字段，只返回必要信息
        fileRecord.setCreateBy(null);
        fileRecord.setCreateTime(null);
        fileRecord.setUpdateBy(null);
        fileRecord.setUpdateTime(null);

        return ResponseEntity.ok(fileRecord);
    }
}
