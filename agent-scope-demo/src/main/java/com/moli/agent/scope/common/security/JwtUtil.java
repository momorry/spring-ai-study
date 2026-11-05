package com.moli.agent.scope.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;

/**
 * JWT 工具类：生成 / 解析 token
 *
 * @author moli
 */
@RequiredArgsConstructor
@Component
public class JwtUtil {

    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_LOGIN_ACCT = "loginAcct";
    private static final String CLAIM_NICK_NAME = "nickName";

    private final JwtProperties jwtProperties;

    private SecretKey secretKey() {
        return Keys.hmacShaKeyFor(Base64.getDecoder().decode(jwtProperties.getSecret()));
    }

    /**
     * 根据登录用户信息生成 JWT token
     */
    public String generateToken(LoginUser user) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + jwtProperties.getExpireSeconds() * 1000);
        return Jwts.builder()
                .subject(String.valueOf(user.getUserId()))
                .claim(CLAIM_USER_ID, user.getUserId())
                .claim(CLAIM_LOGIN_ACCT, user.getLoginAcct())
                .claim(CLAIM_NICK_NAME, user.getNickName())
                .issuedAt(now)
                .expiration(expiration)
                .signWith(secretKey())
                .compact();
    }

    /**
     * 解析并校验 token（签名错误、过期等场景抛出 JwtException / IllegalArgumentException）
     */
    public LoginUser parseToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        LoginUser user = new LoginUser();
        user.setUserId(Long.valueOf(claims.get(CLAIM_USER_ID).toString()));
        user.setLoginAcct(claims.get(CLAIM_LOGIN_ACCT, String.class));
        user.setNickName(claims.get(CLAIM_NICK_NAME, String.class));
        return user;
    }

    /**
     * 获取 token 有效期（秒）
     */
    public long getExpireSeconds() {
        return jwtProperties.getExpireSeconds();
    }

}
