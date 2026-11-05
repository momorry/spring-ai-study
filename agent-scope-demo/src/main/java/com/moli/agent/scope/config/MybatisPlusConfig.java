package com.moli.agent.scope.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 通用配置。
 * <p>
 * 注册分页拦截器 {@link PaginationInnerInterceptor}：MP 的 {@code Page} 分页依赖该拦截器才会真正生效，
 * 否则查询不会追加 LIMIT、total 也不准确。数据库为 TiDB（MySQL 协议），方言使用 {@link DbType#MYSQL}。
 * <p>
 * 注意：3.5.9 起 PaginationInnerInterceptor 已从 mybatis-plus-extension 拆分至独立的
 * mybatis-plus-jsqlparser 模块，pom 中需显式引入该依赖。
 *
 * @author moli
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * 单页最大条数上限，防止前端传入过大的 pageSize 拖垮数据库（-1 表示不限制）。
     */
    private static final long MAX_PAGE_SIZE = 100L;

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(MAX_PAGE_SIZE);
        // 溢出总页数后是否进行处理：true 表示请求页码大于总页数时跳到首页，false 表示继续请求（返回空）
        pagination.setOverflow(false);
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }
}
