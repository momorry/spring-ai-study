package com.moli.agent.scope.controller;

import com.moli.agent.scope.common.base.ApiResult;
import com.moli.agent.scope.common.security.LoginUser;
import com.moli.agent.scope.common.security.UserContext;
import com.moli.agent.scope.common.vo.InLoginVo;
import com.moli.agent.scope.common.vo.InRegisterVo;
import com.moli.agent.scope.common.vo.LoginResultVo;
import com.moli.agent.scope.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录控制器：注册 / 登录 / 登出 / 当前登录用户
 *
 * @author moli
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    /**
     * 注册：登录号仅支持手机号 / 邮箱，注册成功后直接返回 token 实现自动登录
     */
    @PostMapping("/register")
    public ApiResult<LoginResultVo> register(@RequestBody InRegisterVo registerVo) {
        return authService.register(registerVo);
    }

    /**
     * 登录：账号密码换取 JWT token
     */
    @PostMapping("/login")
    public ApiResult<LoginResultVo> login(@RequestBody InLoginVo loginVo) {
        return authService.login(loginVo);
    }

    /**
     * 登出：JWT 无状态，服务端清理用户上下文，客户端丢弃 token 即可
     */
    @PostMapping("/logout")
    public ApiResult<Void> logout() {
        UserContext.clear();
        return ApiResult.ok("登出成功", null);
    }

    /**
     * 获取当前登录用户信息
     */
    @GetMapping("/currentUser")
    public ApiResult<LoginUser> currentUser() {
        return ApiResult.ok(UserContext.get());
    }

}
