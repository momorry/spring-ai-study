package com.moli.agent.scope.harness.artifact;

import java.io.InputStream;

/**
 * 产物字节存储抽象。
 *
 * <p>把「产物落在哪里」与「产物台账怎么记」解耦：{@link ChatArtifactDeliveryTarget} 只依赖本接口，
 * 开发环境用 {@link LocalArtifactStorage} 落本地目录，生产用 {@link OssArtifactStorage} 落 OSS，
 * 台账（m_chat_artifact）与下载接口两边都不用改。
 *
 * <p>这里的 objectKey 统一是「逻辑 key」（形如 {@code 1001/<sessionId>/<uuid>/a.html}），
 * 各实现自行决定怎么落地（本地拼根目录、OSS 拼 key 前缀），因此数据库里的值与后端无关、可迁移。
 *
 * @author moli
 * @since 2026-11-13
 */
public interface ArtifactStorage {

    /**
     * 后端类型，落库到 m_chat_artifact.storage_type，取值见
     * {@link com.moli.agent.scope.common.dao.entity.MChatArtifact#STORAGE_LOCAL} /
     * {@link com.moli.agent.scope.common.dao.entity.MChatArtifact#STORAGE_OSS}。
     * <p>落库时必须写实际承接着这个对象的后端，而不是全局配置：万一日后从 local 切到 oss，
     * 存量记录还得靠这一列找回原来的位置。
     */
    String type();

    /**
     * 写入对象。
     *
     * @param objectKey   逻辑 key（由调用方生成，含 uuid 段以保证唯一）
     * @param content     对象字节
     * @param contentType MIME，供下载时回填响应头
     * @return 实际写入的字节数
     */
    long put(String objectKey, byte[] content, String contentType);

    /**
     * 打开对象输入流，供下载接口流式转发（不要把整个对象读成 byte[]，大文件会顶满堆）。
     * <p>调用方负责关闭返回的流。
     *
     * @param objectKey 逻辑 key
     * @return 对象内容流；对象不存在时返回 {@code null}
     */
    InputStream open(String objectKey);

    /**
     * 删除对象。
     *
     * <p>用于「对象已写入但台账落库失败」的补偿，以及后续的会话删除 / SUPERSEDED 回收任务。
     * 实现不应因对象不存在而抛异常——删除不存在的对象本来就该视为成功。
     *
     * @param objectKey 逻辑 key
     */
    void delete(String objectKey);
}
