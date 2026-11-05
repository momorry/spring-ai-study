package com.moli.agent.scope.common.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 认证配置（对应 application.yml 中 jwt.* 配置项）
 *
 * @author moli
 */
@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /**
     * HS256 签名密钥（Base64 编码，至少 32 字节）
     */
    private String secret;

    /**
     * token 有效期（秒）
     */
    private long expireSeconds = 86400;

    /**
     * token 请求头名称
     */
    private String header = "Authorization";

    /**
     * token 前缀
     */
    private String prefix = "Bearer ";

}
