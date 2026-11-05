package com.moli.agent.scope.service;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.moli.agent.scope.common.base.ApiResult;
import com.moli.agent.scope.common.dao.entity.MUser;
import com.moli.agent.scope.common.dao.manager.MUserManager;
import com.moli.agent.scope.common.security.JwtUtil;
import com.moli.agent.scope.common.security.LoginUser;
import com.moli.agent.scope.common.vo.InLoginVo;
import com.moli.agent.scope.common.vo.LoginResultVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 认证服务：登录（JWT 签发）/ 登出
 *
 * @author moli
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final MUserManager mUserManager;
    private final JwtUtil jwtUtil;

    /**
     * 登录：校验账号密码，签发 JWT token
     */
    public ApiResult<LoginResultVo> login(InLoginVo loginVo) {
        if (loginVo == null || !StringUtils.hasText(loginVo.getLoginAcct())
                || !StringUtils.hasText(loginVo.getPassword())) {
            return ApiResult.fail("登录号和密码不能为空");
        }

        MUser user = mUserManager.getOne(Wrappers.<MUser>lambdaQuery()
                .eq(MUser::getLoginAcct, loginVo.getLoginAcct()));
        // 统一错误提示，避免暴露账号是否存在
        if (user == null || !BCrypt.checkpw(loginVo.getPassword(), user.getPassword())) {
            return ApiResult.fail("登录号或密码错误");
        }
        if (!Boolean.TRUE.equals(user.getStatus())) {
            return ApiResult.fail("账号已注销，请联系管理员");
        }

        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(user.getId());
        loginUser.setLoginAcct(user.getLoginAcct());
        loginUser.setNickName(user.getNickName());

        String token = jwtUtil.generateToken(loginUser);
        log.info("用户登录成功, loginAcct={}, userId={}", user.getLoginAcct(), user.getId());

        return ApiResult.ok("登录成功", LoginResultVo.builder()
                .token(token)
                .expiresIn(jwtUtil.getExpireSeconds())
                .userId(user.getId())
                .loginAcct(user.getLoginAcct())
                .nickName(user.getNickName())
                .build());
    }

}
