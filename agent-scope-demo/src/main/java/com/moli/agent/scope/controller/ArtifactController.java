package com.moli.agent.scope.controller;

import com.moli.agent.scope.common.dao.entity.MChatArtifact;
import com.moli.agent.scope.common.dao.manager.MChatArtifactManager;
import com.moli.agent.scope.common.security.UserContext;
import com.moli.agent.scope.harness.artifact.ArtifactStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 产物下载控制器。
 *
 * <p>入参是 <b>artifactId</b>（m_chat_artifact 主键）而不是 messageId：一条回答可以交付多个文件，
 * 按消息找只能下载到一个；而且消息 ID 是能被前端预生成、被日志记录的值，拿它当下载凭据等于让别人猜得到。
 *
 * <p>只认 artifactId 还顺带堵掉了旧实现的一个越权口子：旧下载接口按 messageId 查记录时不带 user_id 条件，
 * 任何登录用户只要知道别人的消息 ID 就能取走别人的文件。这里每次都会把台账的 user_id 与 JWT 身份比一遍，
 * 不属于当前用户的一律按「不存在」处理（不区分「没有」和「不是你的」，否则等于对外探测产物 ID 是否存在）。
 *
 * @author moli
 * @since 2026-11-13
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/artifact")
public class ArtifactController {

    private final MChatArtifactManager artifactManager;

    /**
     * 当前生效的产物存储后端（local 或 oss，由 agent.artifact.storage 决定，装配见 ArtifactStorageConfiguration）。
     */
    private final ArtifactStorage artifactStorage;

    /**
     * 下载产物：从存储后端取出对象并流式转发，不把整个文件读进堆内存。
     *
     * @param artifactId 产物ID
     * @return 产物内容；不存在、不属于当前用户、或对象已被回收时返回 404
     */
    @GetMapping("/download")
    public ResponseEntity<StreamingResponseBody> download(@RequestParam("artifactId") Long artifactId) {
        MChatArtifact artifact = loadOwnedActive(artifactId);
        if (artifact == null) {
            return ResponseEntity.notFound().build();
        }
        if (!artifactStorage.type().equals(artifact.getStorageType())) {
            // 台账记的是「当时由哪个后端承接」，与现在配置的可能不一致（例如从 local 切到了 oss）。
            // 这时绝不能拿当前后端去试开那个 key：要么读到一个同名的别人的对象，要么莫名其妙 500
            log.warn("产物存储后端与当前配置不一致，无法下载: artifactId={}, 记录后端={}, 当前后端={}",
                    artifactId, artifact.getStorageType(), artifactStorage.type());
            return ResponseEntity.notFound().build();
        }

        InputStream in;
        try {
            in = artifactStorage.open(artifact.getObjectKey());
        } catch (Exception e) {
            log.error("打开产物对象失败: artifactId={}, objectKey={}", artifactId, artifact.getObjectKey(), e);
            return ResponseEntity.internalServerError().build();
        }
        if (in == null) {
            // 台账还在但对象没了（被生命周期规则清掉、人工删过）：对用户只能说「取不到了」，别报 500
            log.warn("产物对象不存在: artifactId={}, objectKey={}", artifactId, artifact.getObjectKey());
            return ResponseEntity.notFound().build();
        }

        String fileName = artifact.getFileName();
        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        MediaType mediaType = resolveMediaType(artifact.getContentType());

        // 交给 StreamingResponseBody 而不是 InputStreamResource：前者由我们 own 这个流的关闭，
        // 后者能否被关取决于消息转换器实现，客户端中途断开就可能留下没关的连接
        StreamingResponseBody body = outputStream -> {
            try (InputStream stream = in) {
                stream.transferTo(outputStream);
            }
        };
        // attachment：一律强制下载。产物内容是模型生成的，HTML/SVG 里带脚本的话，
        // inline 预览等于在本域执行来历不明的代码（要预览得另开独立域名 + iframe sandbox）
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encodedFileName);
        if (artifact.getFileSize() != null && artifact.getFileSize() > 0) {
            builder.contentLength(artifact.getFileSize());
        }
        return builder.body(body);
    }

    /**
     * 按主键加载产物，并校验归属当前登录用户且仍可用；不存在、越权或已被覆盖/删除时返回 null。
     */
    private MChatArtifact loadOwnedActive(Long artifactId) {
        if (artifactId == null) {
            return null;
        }
        Long userId = UserContext.getUserId();
        MChatArtifact artifact = artifactManager.getById(artifactId);
        if (artifact == null || userId == null || !userId.equals(artifact.getUserId())) {
            if (artifact != null) {
                // 有这条记录但不是当前用户的：说明有人拿别人的 artifactId 在试，留一行日志便于事后追责
                log.warn("越权访问产物被拒: 请求用户={}, 产物归属={}, artifactId={}",
                        userId, artifact.getUserId(), artifactId);
            }
            return null;
        }
        if (!MChatArtifact.STATUS_ACTIVE.equals(artifact.getStatus())) {
            log.info("产物已不可用: artifactId={}, status={}", artifactId, artifact.getStatus());
            return null;
        }
        return artifact;
    }

    /**
     * 下载响应的 Content-Type。
     * <p>台账里推不出类型时按二进制给：宁可让用户下载后手动选打开方式，
     * 也不能把来历不明的内容当 text/html 直出去。
     */
    private MediaType resolveMediaType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(contentType);
        } catch (Exception e) {
            log.warn("台账里的 contentType 无法解析，按二进制下载: {}", contentType);
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
