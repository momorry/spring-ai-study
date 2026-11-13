package com.moli.agent.scope.harness.artifact;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.ObjectMetadata;
import com.moli.agent.scope.common.dao.entity.MChatArtifact;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * 阿里云 OSS 产物存储。
 *
 * <p>只在 {@code agent.artifact.storage=oss} 时被装配（见 {@link ArtifactStorageConfiguration}），
 * 因此本地开发不配 OSS 也不会因为缺 SDK 客户端而启动失败。
 *
 * <p>注意这里只把 OSS 当「对象桶」用：一次交付 = 一次 PutObject，读一次 = 一次 GetObject。
 * 不要把它当 KV 存储去承载 Agent 工作区的频繁小读写（那是 {@code RemoteFilesystemSpec} + Redis/MySQL BaseStore
 * 的位置），否则延迟与按请求计费都会失控。
 *
 * @author moli
 * @since 2026-11-13
 */
@Slf4j
public class OssArtifactStorage implements ArtifactStorage, AutoCloseable {

    private final OSS ossClient;

    private final String bucket;

    /** 实际对象名 = keyPrefix + 逻辑 key；前缀只在这里拼接，数据库里存的是不带前缀的逻辑 key */
    private final String keyPrefix;

    public OssArtifactStorage(ArtifactProperties properties) {
        ArtifactProperties.Oss oss = properties.getOss();
        if (isBlank(oss.getEndpoint()) || isBlank(oss.getBucket())
                || isBlank(oss.getAccessKeyId()) || isBlank(oss.getAccessKeySecret())) {
            throw new IllegalStateException("agent.artifact.storage=oss 但 agent.artifact.oss.{endpoint,bucket,"
                    + "access-key-id,access-key-secret} 未配齐。本地请改用 storage=local");
        }
        this.bucket = oss.getBucket();
        this.keyPrefix = normalizePrefix(oss.getKeyPrefix());
        this.ossClient = new OSSClientBuilder().build(oss.getEndpoint(), oss.getAccessKeyId(), oss.getAccessKeySecret());
        log.info("产物存储使用 OSS: bucket={}, keyPrefix={}", bucket, keyPrefix);
    }

    @Override
    public String type() {
        return MChatArtifact.STORAGE_OSS;
    }

    @Override
    public long put(String objectKey, byte[] content, String contentType) {
        ObjectMetadata metadata = new ObjectMetadata();
        // 必须显式给长度，否则 SDK 走 chunked 传输，部分网关会拒
        metadata.setContentLength(content.length);
        metadata.setContentType(isBlank(contentType) ? "application/octet-stream" : contentType);
        ossClient.putObject(bucket, fullKey(objectKey), new ByteArrayInputStream(content), metadata);
        return content.length;
    }

    @Override
    public InputStream open(String objectKey) {
        String key = fullKey(objectKey);
        if (!ossClient.doesObjectExist(bucket, key)) {
            // 返回 null 让上层给出「产物不存在」的 404，而不是把 SDK 的 NoSuchKey 异常抛成 500
            return null;
        }
        OSSObject object = ossClient.getObject(bucket, key);
        // 关掉这个流即归还底层 HTTP 连接，无需再关 OSSObject 本身
        return object.getObjectContent();
    }

    @Override
    public void delete(String objectKey) {
        // DeleteObject 对不存在的 key 同样返回 204，天然幂等
        ossClient.deleteObject(bucket, fullKey(objectKey));
    }

    @Override
    public void close() {
        ossClient.shutdown();
    }

    private String fullKey(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("objectKey 不能为空");
        }
        // 挡掉 ../ 之类的越界写法：OSS 没有目录概念，但 key 里带 ../ 会让对账与生命周期规则失效
        String cleaned = objectKey.replace("../", "").replace("..\\", "");
        return keyPrefix + cleaned;
    }

    private static String normalizePrefix(String prefix) {
        if (isBlank(prefix)) {
            return "";
        }
        return prefix.endsWith("/") ? prefix : prefix + "/";
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
