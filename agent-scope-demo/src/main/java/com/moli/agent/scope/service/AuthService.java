package com.moli.agent.scope.service;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.moli.agent.scope.common.base.ApiResult;
import com.moli.agent.scope.common.dao.entity.MUser;
import com.moli.agent.scope.common.dao.manager.MUserManager;
import com.moli.agent.scope.common.security.JwtUtil;
import com.moli.agent.scope.common.security.LoginUser;
import com.moli.agent.scope.common.vo.InLoginVo;
import com.moli.agent.scope.common.vo.InRegisterVo;
import com.moli.agent.scope.common.vo.LoginResultVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

/**
 * 认证服务：注册 / 登录（JWT 签发）/ 登出
 *
 * @author moli
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    /**
     * 手机号：11 位，1 开头，第二位 3-9
     */
    private static final Pattern MOBILE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    /**
     * 邮箱：常见格式，总长度另行按字段长度限制校验
     */
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private static final String LOGIN_ACCT_TYPE_MOBILE = "mobile";
    private static final String LOGIN_ACCT_TYPE_EMAIL = "email";

    private final MUserManager mUserManager;
    private final JwtUtil jwtUtil;

    /**
     * 注册：登录号仅支持手机号 / 邮箱，校验通过后 BCrypt 加密入库，并直接签发 token 实现自动登录
     */
    public ApiResult<LoginResultVo> register(InRegisterVo registerVo) {
        if (registerVo == null || !StringUtils.hasText(registerVo.getLoginAcct())
                || !StringUtils.hasText(registerVo.getPassword())) {
            return ApiResult.fail("登录号和密码不能为空");
        }

        String loginAcct = registerVo.getLoginAcct().trim();
        // 长度与 login_acct 字段 varchar(32) 对齐，避免超长内容带 DB 异常落库失败
        if (loginAcct.length() > 32) {
            return ApiResult.fail("登录号长度不能超过 32 个字符");
        }
        // 账号仅允许手机号或邮箱两种类型
        String loginAcctType;
        if (MOBILE_PATTERN.matcher(loginAcct).matches()) {
            loginAcctType = LOGIN_ACCT_TYPE_MOBILE;
        } else if (EMAIL_PATTERN.matcher(loginAcct).matches()) {
            loginAcctType = LOGIN_ACCT_TYPE_EMAIL;
        } else {
            return ApiResult.fail("登录号仅支持手机号或邮箱");
        }
        if (registerVo.getPassword().length() < 6 || registerVo.getPassword().length() > 32) {
            return ApiResult.fail("密码长度需在 6-32 位之间");
        }
        String nickName = StringUtils.hasText(registerVo.getNickName())
                ? registerVo.getNickName().trim() : loginAcct;
        if (nickName.length() > 32) {
            return ApiResult.fail("昵称长度不能超过 32 个字符");
        }

        // 业务层先查重给出友好提示；数据库 uq_la 唯一索引兜底防并发重复
        boolean exists = mUserManager.exists(Wrappers.<MUser>lambdaQuery()
                .eq(MUser::getLoginAcct, loginAcct));
        if (exists) {
            return ApiResult.fail("该登录号已被注册");
        }

        MUser user = new MUser();
        user.setLoginAcct(loginAcct);
        user.setLoginAcctType(loginAcctType);
        user.setNickName(nickName);
        // user_name 表字段非空，注册未收集真实姓名，与历史种子数据一致先用昵称占位
        user.setUserName(nickName);
        user.setPassword(BCrypt.hashpw(registerVo.getPassword()));
        user.setStatus(true);
        mUserManager.save(user);
        log.info("用户注册成功, loginAcct={}, loginAcctType={}, userId={}", loginAcct, loginAcctType, user.getId());

        // 注册即登录：签发 token，逻辑与 login 保持一致
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(user.getId());
        loginUser.setLoginAcct(user.getLoginAcct());
        loginUser.setNickName(user.getNickName());

        String token = jwtUtil.generateToken(loginUser);
        return ApiResult.ok("注册成功", LoginResultVo.builder()
                .token(token)
                .expiresIn(jwtUtil.getExpireSeconds())
                .userId(user.getId())
                .loginAcct(user.getLoginAcct())
                .nickName(user.getNickName())
                .build());
    }

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
