package com.moli.agent.scope.harness.artifact;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 对话产物存储配置（对应 application.yml 中 agent.artifact.*）。
 *
 * <p>产物字节落哪儿由 {@code storage} 决定，台账（m_chat_artifact）永远落 MySQL：
 * 两者是分开的两件事 —— 台账要可索引/可鉴权/可回收，所以必须是关系表，不能跟着对象存储走。
 *
 * @author moli
 * @since 2026-11-13
 */
@Data
@Component
@ConfigurationProperties(prefix = "agent.artifact")
public class ArtifactProperties {

    /**
     * 存储后端：{@code local}（默认，落本地目录，开发环境用）/ {@code oss}（落阿里云 OSS）。
     * <p>生产环境必须切成 oss：local 后端下产物只存在于当前那台机器的盘上，多副本时用户可能取不到。
     */
    private String storage = "local";

    /**
     * 单个产物允许的最大字节数（MB），超限直接拒绝交付。
     * <p>deliver_artifact 是把整个文件读成 byte[] 再交接的，不设上限等于把堆内存交给模型摆布。
     */
    private int maxSizeMb = 50;

    /** 本地目录后端配置 */
    private final Local local = new Local();

    /** 阿里云 OSS 后端配置 */
    private final Oss oss = new Oss();

    @Data
    public static class Local {

        /**
         * 本地产物根目录，{@code object_key} 相对它解析。
         * <p>默认 ./output：与旧链路 {@code FileGenMiddleware.OUTPUT_DIR} 保持一致，
         * 这样迁移脚本只改元数据、不需要搬动存量文件（旧记录的 object_key 就是裸文件名）。
         */
        private String root = "./output";
    }

    @Data
    public static class Oss {

        /** OSS 服务接入点，如 https://oss-cn-hangzhou.aliyuncs.com */
        private String endpoint;

        /** 存储桶名称 */
        private String bucket;

        /** 访问密钥 ID（生产建议走环境变量 / STS，不要写进配置文件） */
        private String accessKeyId;

        /** 访问密钥密文（同上） */
        private String accessKeySecret;

        /**
         * key 前缀，用于在同一 bucket 内隔离本应用的产物，并方便配置按前缀的生命周期规则。
         * <p>不落进 m_chat_artifact.object_key：表里存的是「逻辑 key」，实际对象名 = 本前缀 + 逻辑 key，
         * 换前缀时只需同步改这里的配置与对账公式。
         */
        private String keyPrefix = "artifacts/";
    }
}
