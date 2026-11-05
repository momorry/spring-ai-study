package com.moli.agent.scope.common.vo;

import lombok.Data;

/**
 * 登录请求 VO
 *
 * @author moli
 */
@Data
public class InLoginVo {

    /**
     * 登录号
     */
    private String loginAcct;

    /**
     * 密码（明文，服务端 BCrypt 校验）
     */
    private String password;

}
