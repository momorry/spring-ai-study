package com.moli.agent.scope.config;

import com.moli.agent.scope.common.security.LoginInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 通用配置：
 * 1. 注册登录拦截器，拦截 /**，排除 /health 及登录相关端点；
 * 2. 配置异步请求支持，为大模型 SSE 流式对话（返回 Flux）提供线程池与超时，
 * 避免使用默认的 SimpleAsyncTaskExecutor（不复用线程）。
 *
 * @author moli
 */
@Configuration
@RequiredArgsConstructor
public class CommonWebConfig implements WebMvcConfigurer {

    /**
     * 异步（SSE 流式）请求超时时间（毫秒）。流式对话可能持续较久，设置较大值；<=0 表示不超时。
     */
    private static final long ASYNC_TIMEOUT_MILLIS = 10 * 60 * 1000L;

    private final LoginInterceptor loginInterceptor;

    /**
     * 注册登录拦截器：拦截路径 /**，排除 /health 与免登录端点。
     * 说明：/auth/login、/auth/logout 必须排除，否则无法完成登录；/auth/currentUser 仍需鉴权。
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/health",
                        "/auth/login",
                        "/auth/logout",
                        "/error"
                );
    }

    /**
     * 异步支持：Spring MVC 处理 Flux/Mono 返回值（SSE 流式）时会开启 Servlet 异步，
     * 这里显式指定线程池与超时。
     */
    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(mvcAsyncTaskExecutor());
        configurer.setDefaultTimeout(ASYNC_TIMEOUT_MILLIS);
    }

    /**
     * MVC 异步请求线程池（用于 SSE / 响应式返回值适配的异步派发）
     */
    @Bean(name = "mvcAsyncTaskExecutor")
    public ThreadPoolTaskExecutor mvcAsyncTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(32);
        executor.setQueueCapacity(256);
        executor.setThreadNamePrefix("mvc-async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

}
