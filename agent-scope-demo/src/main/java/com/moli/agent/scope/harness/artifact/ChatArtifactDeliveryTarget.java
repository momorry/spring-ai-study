package com.moli.agent.scope.harness.artifact;

import com.moli.agent.scope.common.dao.entity.MChatArtifact;
import com.moli.agent.scope.common.dao.manager.MChatArtifactManager;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.harness.agent.artifact.ArtifactDeliveryRequest;
import io.agentscope.harness.agent.artifact.ArtifactDeliveryResult;
import io.agentscope.harness.agent.artifact.ArtifactDeliveryTarget;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * 产物交付落地实现：把 {@code deliver_artifact} 交接过来的字节存进
 * {@link ArtifactStorage}（OSS 或本地目录），并在 m_chat_artifact 记一条台账。
 *
 * <p>框架只负责把字节从 Agent 工作区搬出来（{@code AbstractFilesystem.downloadFiles}），
 * 「存哪儿、怎么记账、谁能下载」全是本类的职责。三条配置轴是正交的：会话状态（stateStore → MySQL）、
 * 工作区后端（filesystem）、产物出口（本类）互不依赖，所以产物上 OSS 不需要动 stateStore，
 * 也不需要 {@code RemoteFilesystemSpec}。
 *
 * <p>两个刻意的取舍：
 * 一、<b>先落对象、再落台账，台账失败就删对象</b>。反过来会留下「有记录无对象」的死链，
 * 用户点下载永远 404，比留一个没人引用的孤儿对象更难排查。
 * 二、<b>message_id 这里填不了</b>。交付发生在本轮答完之前，那条最终 assistant 消息还不存在，
 * 只能先按 (user_id, session_id) 落库，等本轮结束时回填；回填不上也照样能按会话列出来、能下载。
 *
 * @author moli
 * @since 2026-11-13
 */
@Slf4j
@Component
public class ChatArtifactDeliveryTarget implements ArtifactDeliveryTarget {

    /**
     * 框架内置交付工具 {@code ArtifactDeliveryTool} 的工具名，事件流侧靠它识别哪次工具调用交了产物。
     * <p>SDK 里没有公开常量（只在 {@code @Tool(name = "deliver_artifact")} 注解里写了字面量），
     * 升级 SDK 时若工具改名，这里不会编译报错，只会静默地不再下发产物卡片，改版本后要跟一遍。
     */
    public static final String DELIVER_TOOL_NAME = "deliver_artifact";

    /** 文件名列长度 256，留出余量；过长直接截断而不是拒交付（模型偶尔会生成超长串名） */
    private static final int FILE_NAME_MAX_LENGTH = 200;

    /** 说明列长度 512 */
    private static final int DESCRIPTION_MAX_LENGTH = 500;

    private static final int SESSION_ID_MAX_LENGTH = 64;

    private final ArtifactStorage storage;

    private final MChatArtifactManager artifactManager;

    private final ArtifactProperties properties;

    public ChatArtifactDeliveryTarget(ArtifactStorage storage,
                                      MChatArtifactManager artifactManager,
                                      ArtifactProperties properties) {
        this.storage = storage;
        this.artifactManager = artifactManager;
        this.properties = properties;
    }

    @Override
    public ArtifactDeliveryResult deliver(RuntimeContext runtimeContext, ArtifactDeliveryRequest request) {
        if (request == null) {
            return ArtifactDeliveryResult.fail("交付请求为空");
        }
        // runtimeContext 在无 merged context 时可能为 null，userId/sessionId 是台账的归属与鉴权依据，缺一样都不能落
        Long userId = resolveUserId(runtimeContext);
        String sessionId = runtimeContext == null ? null : trimToNull(runtimeContext.getSessionId());
        if (userId == null || sessionId == null) {
            log.warn("[ArtifactDelivery] 缺少 userId/sessionId 上下文，拒绝交付: filePath={}, userId={}, sessionId={}",
                    request.filePath(), userId, sessionId);
            return ArtifactDeliveryResult.fail("missing user/session context, cannot record artifact ownership");
        }
        byte[] content = request.content();
        if (content == null || content.length == 0) {
            return ArtifactDeliveryResult.fail("file is empty, nothing to deliver");
        }
        long maxBytes = (long) Math.max(properties.getMaxSizeMb(), 1) * 1024 * 1024;
        if (content.length > maxBytes) {
            // deliver_artifact 是整文件读成 byte[] 再交接的，不设上限等于让模型把堆内存当垃圾桶
            return ArtifactDeliveryResult.fail("file too large: " + content.length
                    + " bytes, limit " + maxBytes + " bytes");
        }
        String fileName = resolveFileName(request);
        if (fileName == null) {
            return ArtifactDeliveryResult.fail("unable to derive a safe file name from '"
                    + request.fileName() + "'");
        }

        boolean hasSameName = artifactManager.hasActiveArtifact(userId, sessionId, fileName);
        if (hasSameName && !request.force()) {
            // 交给框架去引导模型换名重试，而不是我们悄悄改名：改了名用户就找不到自己说的那个文件了
            return ArtifactDeliveryResult.conflict("an ACTIVE artifact named '" + fileName
                    + "' already exists in this session; retry with a different fileName or force=true");
        }

        String objectKey = buildObjectKey(userId, sessionId, fileName);
        String contentType = resolveContentType(fileName);
        storage.put(objectKey, content, contentType);

        Long artifactId;
        try {
            MChatArtifact artifact = new MChatArtifact();
            artifact.setUserId(userId);
            artifact.setSessionId(sessionId);
            // message_id 留空，由本轮结束时回填（见类注释）
            artifact.setFileName(fileName);
            artifact.setStorageType(storage.type());
            artifact.setObjectKey(objectKey);
            artifact.setSourcePath(trimToNull(request.filePath()));
            artifact.setFileSize((long) content.length);
            artifact.setContentType(contentType);
            artifact.setDescription(cut(trimToNull(request.description()), DESCRIPTION_MAX_LENGTH));
            artifact.setStatus(MChatArtifact.STATUS_ACTIVE);
            artifactId = artifactManager.saveArtifact(artifact);
        } catch (Exception e) {
            // 对象已经落下来了但台账没记上：这条链接没人知道、也没法被回收，立刻补偿删除
            safeDelete(objectKey);
            log.error("[ArtifactDelivery] 台账落库失败，已回滚对象: userId={}, sessionId={}, fileName={}, objectKey={}",
                    userId, sessionId, fileName, objectKey, e);
            return ArtifactDeliveryResult.fail("failed to record artifact: " + e.getMessage());
        }

        if (hasSameName) {
            // 新台账已经记好，才把旧的置为 SUPERSEDED：顺序反过来的话，新记录插入失败会把旧产物一起拖成不可用
            // 这一步只改状态，失败也不影响新产物可用，所以不阻断交付
            try {
                artifactManager.supersedeSameName(userId, sessionId, fileName);
            } catch (Exception e) {
                log.warn("[ArtifactDelivery] 旧产物置 SUPERSEDED 失败（新产物不受影响）: userId={}, sessionId={}, fileName={}",
                        userId, sessionId, fileName, e);
            }
        }

        log.info("[ArtifactDelivery] 产物已交付: artifactId={}, userId={}, sessionId={}, fileName={}, size={}, storage={}",
                artifactId, userId, sessionId, fileName, content.length, storage.type());
        // 这段文本会拼进工具返回值给模型看（框架再包一层 "Delivered ... : {message}"），保持一行、只放定位信息
        return ArtifactDeliveryResult.success("artifactId=" + artifactId + ", fileName=" + fileName
                + ", size=" + content.length + " bytes, stored in " + storage.type());
    }

    /**
     * 解析归属用户。
     * <p>RuntimeContext 的 userId 是字符串，本项目的 m_user.id 是 bigint，非数字（比如匿名 id）就不能落台账，
     * 否则会写进一个查不到主人的归属值。
     */
    private Long resolveUserId(RuntimeContext runtimeContext) {
        String raw = runtimeContext == null ? null : trimToNull(runtimeContext.getUserId());
        if (raw == null) {
            return null;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            log.warn("[ArtifactDelivery] userId 不是数字，无法作为归属键: userId={}", raw);
            return null;
        }
    }

    /**
     * 定下最终文件名。
     * <p>框架已经保证传进来的 fileName 是裸文件名（不含路径分隔符、{@code .}、{@code ..}），这里仍再洗一遍：
     * 台账里的 file_name 会直接进下载响应的 Content-Disposition，Windows 上 {@code : * ? " < > |}
     * 这些字符会把下载整个弄失败。
     */
    private String resolveFileName(ArtifactDeliveryRequest request) {
        String candidate = trimToNull(request.fileName());
        if (candidate == null) {
            // fileName 可空，缺省时由 filePath 的 basename 推（框架也是这么处理的，这里保持一致）
            String path = trimToNull(request.filePath());
            if (path == null) {
                return null;
            }
            String normalized = path.replace('\\', '/');
            int slash = normalized.lastIndexOf('/');
            candidate = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        }
        String cleaned = candidate.replace('/', '_').replace('\\', '_')
                .replaceAll("[<>:\"|?*\\p{Cntrl}]", "_")
                // Windows 不允许文件名以点或空格结尾，会被静默截断成另一个名字，导致台账与实际文件对不上
                .replaceAll("[ .]+$", "")
                .trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        return cut(cleaned, FILE_NAME_MAX_LENGTH);
    }

    /**
     * 逻辑 key：{userId}/{sessionId}/{uuid}/{fileName}
     * <p>带 userId 段是为了能按用户配 OSS 生命周期规则、做容量统计与对账（鉴权不依赖它，认的是表里的 user_id）；
     * 带 uuid 段是为了同名覆盖时新旧对象不互相顶掉，同时让 key 不可被猜测遍历。
     */
    private String buildObjectKey(Long userId, String sessionId, String fileName) {
        String sessionSegment = cut(sessionId.replace('/', '_'), SESSION_ID_MAX_LENGTH);
        String unique = UUID.randomUUID().toString().replace("-", "");
        return userId + "/" + sessionSegment + "/" + unique + "/" + fileName;
    }

    private String resolveContentType(String fileName) {
        // 按扩展名推断（Spring 自带的映射表），推不出来就当二进制下载：
        // 宁可不预览，也不能把来历不明的内容当 text/html 直出去
        Optional<MediaType> mediaType = MediaTypeFactory.getMediaType(fileName);
        return mediaType.map(MediaType::toString).orElse("application/octet-stream");
    }

    private void safeDelete(String objectKey) {
        try {
            storage.delete(objectKey);
        } catch (Exception e) {
            log.error("[ArtifactDelivery] 补偿删除对象失败，会留下孤儿对象（需按 objectKey 人工清理）: {}",
                    objectKey, e);
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String cut(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
