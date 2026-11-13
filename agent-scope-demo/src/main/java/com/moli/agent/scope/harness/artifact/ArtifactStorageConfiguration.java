package com.moli.agent.scope.harness.artifact;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 产物存储后端装配：按 {@code agent.artifact.storage} 二选一。
 *
 * <p>两个 Bean 用互斥的 {@link ConditionalOnProperty} 而不是 {@code @ConditionalOnMissingBean}：
 * 后者依赖 Bean 定义的注册顺序，在普通 {@code @Configuration} 里顺序不保证，容易出现
 * 「本地实现先把 OSS 实现顶掉」这种只在特定扫描顺序下才复现的问题。
 *
 * @author moli
 * @since 2026-11-13
 */
@Configuration(proxyBeanMethods = false)
public class ArtifactStorageConfiguration {

    /**
     * OSS 后端：生产环境显式配 {@code agent.artifact.storage=oss} 才装配，
     * 这样本地开发不配 AK/SK 也能正常启动。
     */
    @Bean
    @ConditionalOnProperty(prefix = "agent.artifact", name = "storage", havingValue = "oss")
    public ArtifactStorage ossArtifactStorage(ArtifactProperties properties) {
        return new OssArtifactStorage(properties);
    }

    /**
     * 本地目录后端：缺省即启用（未配置时相当于 {@code storage=local}），作为开发兜底。
     */
    @Bean
    @ConditionalOnProperty(prefix = "agent.artifact", name = "storage", havingValue = "local", matchIfMissing = true)
    public ArtifactStorage localArtifactStorage(ArtifactProperties properties) {
        return new LocalArtifactStorage(properties);
    }
}
