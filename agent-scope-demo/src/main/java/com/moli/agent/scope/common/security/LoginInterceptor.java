package com.moli.agent.scope.common.security;

import com.moli.agent.scope.common.base.ApiResult;
import com.moli.agent.scope.common.base.EnumErrorCode;
import com.moli.agent.scope.common.util.JsonUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.AsyncHandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * 登录拦截器：校验请求头中是否存在合法的 JWT TOKEN
 * <p>
 * 基于 Spring MVC 的 {@link AsyncHandlerInterceptor} 实现（拦截路径与排除路径在
 * {@code CommonWebConfig} 中通过 addPathPatterns / excludePathPatterns 配置）：
 * 1. preHandle：解析并校验 Bearer token，失败直接写回 401 并中断请求；
 * 2. 校验通过后将用户信息写入 {@link UserContext}（ThreadLocal，绑定当前 servlet 线程）；
 * 3. 普通请求在 afterCompletion 清理；异步请求（如 SSE 流式对话）在
 * afterConcurrentHandlingStarted 清理，避免 servlet 线程被复用时上下文串号 / 泄漏。
 *
 * @author moli
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class LoginInterceptor implements AsyncHandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final JwtProperties jwtProperties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        // CORS 预检请求直接放行
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        // 解析请求头中的 JWT TOKEN
        String token = resolveToken(request);
        if (token == null || token.isBlank()) {
            writeUnauthorized(response, "未登录：请求头中缺少 JWT TOKEN");
            return false;
        }

        LoginUser loginUser;
        try {
            loginUser = jwtUtil.parseToken(token);
        } catch (Exception e) {
            log.warn("JWT 校验失败, path={}, reason={}", request.getRequestURI(), e.getMessage());
            writeUnauthorized(response, "登录已过期或 TOKEN 无效，请重新登录");
            return false;
        }

        // 写入全局用户上下文（当前 servlet 线程内有效）
        UserContext.set(loginUser);
        return true;
    }

    /**
     * 异步请求（如返回 Flux 的 SSE 流式对话）开始处理后，servlet 线程即将被释放回线程池，
     * 此时清理 ThreadLocal，防止线程复用导致的用户信息串号 / 内存泄漏。
     */
    @Override
    public void afterConcurrentHandlingStarted(HttpServletRequest request, HttpServletResponse response,
                                               Object handler) {
        UserContext.clear();
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                Exception ex) {
        UserContext.clear();
    }

    private String resolveToken(HttpServletRequest request) {
        // 优先从配置的请求头获取（如 Authorization: Bearer xxx）
        String value = request.getHeader(jwtProperties.getHeader());
        if (value != null && !value.isBlank()) {
            String prefix = jwtProperties.getPrefix();
            if (prefix != null && !prefix.isBlank() && value.startsWith(prefix)) {
                return value.substring(prefix.length()).trim();
            }
            return value.trim();
        }
        // 兜底：SSE / EventSource 等无法自定义请求头的场景，支持 token 查询参数
        return request.getParameter("token");
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ApiResult<Void> body = ApiResult.fail(EnumErrorCode.UNAUTHORIZED.getCode(), message);
        // 复用全局唯一的 ObjectMapper（由 JsonUtil 统一管理），保证与 Spring MVC 序列化配置一致
        response.getWriter().write(JsonUtil.toJson(body));
    }

}
