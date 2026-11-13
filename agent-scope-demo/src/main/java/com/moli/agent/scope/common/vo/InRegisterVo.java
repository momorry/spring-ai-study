package com.moli.agent.scope.common.vo;

import lombok.Data;

/**
 * 注册请求 VO：登录号仅支持手机号 / 邮箱
 *
 * @author moli
 */
@Data
public class InRegisterVo {

    /**
     * 登录号（仅支持手机号或邮箱）
     */
    private String loginAcct;

    /**
     * 密码（明文，服务端 BCrypt 加密存储）
     */
    private String password;

    /**
     * 用户昵称
     */
    private String nickName;

}
