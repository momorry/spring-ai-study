package com.moli.agent.scope.common.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 登录结果 VO
 *
 * @author moli
 */
@Data
@Builder
public class LoginResultVo {

    /**
     * JWT token
     */
    private String token;

    /**
     * token 有效期（秒）
     */
    private Long expiresIn;

    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * 登录号
     */
    private String loginAcct;

    /**
     * 用户昵称
     */
    private String nickName;

}
