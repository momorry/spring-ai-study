package com.moli.agent.scope.common.security;

import lombok.Data;

/**
 * 登录用户简单信息（放入 JWT 声明与用户上下文）
 *
 * @author moli
 */
@Data
public class LoginUser {

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
