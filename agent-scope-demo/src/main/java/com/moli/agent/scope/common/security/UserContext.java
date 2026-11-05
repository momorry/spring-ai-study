package com.moli.agent.scope.common.security;

/**
 * 全局用户上下文（ThreadLocal 封装）
 * <p>
 * 由 {@link LoginInterceptor} 在 preHandle 写入，绑定当前 servlet 请求线程，
 * 请求结束（afterCompletion）或异步处理开始（afterConcurrentHandlingStarted）时清理。
 * <p>
 * 注意：ThreadLocal 仅在同一个 servlet 请求线程内有效。大模型流式对话返回 Flux 后，
 * AgentScope 会在其内部的 boundedElastic 线程池上执行工具 / 中间件，
 * 这些线程读不到本上下文。若工具需要当前用户信息，应通过 AgentScope 的
 * ToolExecutionContext 注入，或在进入 Agent 前取出 {@link LoginUser} 显式传参。
 *
 * @author moli
 */
public final class UserContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    /**
     * 写入当前登录用户
     */
    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    /**
     * 获取当前登录用户，未登录返回 null
     */
    public static LoginUser get() {
        return HOLDER.get();
    }

    /**
     * 获取当前登录用户 ID，未登录返回 null
     */
    public static Long getUserId() {
        LoginUser user = HOLDER.get();
        return user == null ? null : user.getUserId();
    }

    /**
     * 清理上下文（必须在请求结束时调用，防止线程复用导致的信息串号 / 内存泄漏）
     */
    public static void clear() {
        HOLDER.remove();
    }

}
