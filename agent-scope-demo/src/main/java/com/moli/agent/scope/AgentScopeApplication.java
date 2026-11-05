package com.moli.agent.scope;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * AgentScope Demo 应用启动类
 * 
 * @author AgentScope Demo
 * @since 2026-11-02
 */
@SpringBootApplication
@MapperScan("com.moli.agent.scope.common.dao.mapper")
public class AgentScopeApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentScopeApplication.class, args);
    }
}
