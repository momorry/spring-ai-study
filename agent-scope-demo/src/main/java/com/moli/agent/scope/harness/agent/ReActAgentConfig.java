package com.moli.agent.scope.harness.agent;

import com.moli.agent.scope.common.dao.manager.MChatMessageFileManager;
import com.moli.agent.scope.harness.middleware.FileGenMiddleware;
import com.moli.agent.scope.harness.model.ModelCreator;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.extensions.mysql.state.MysqlAgentStateStore;
import io.agentscope.harness.agent.HarnessAgent;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.utils.Lists;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

@Slf4j
@Configuration
public class ReActAgentConfig {

    /** 状态表所在库名；留空则自动取数据源当前库（避免默认落到不存在的 agentscope 库） */
    @Value("${agentscope.state-store.database:}")
    private String configuredDatabase;

    /** 会话状态表名 */
    @Value("${agentscope.state-store.table:agentscope_sessions}")
    private String stateTable;

    /** 是否在启动时自动建表 */
    @Value("${agentscope.state-store.auto-create-table:true}")
    private boolean autoCreateTable;

    /**
     * MySQL 会话状态存储（基于 agentscope-extensions-mysql）。
     * <p>把每个 (userId, sessionId) 的对话状态持久化到 MySQL，
     * 使多轮对话上下文可跨请求、跨重启恢复。
     */
    @Bean
    public MysqlAgentStateStore mysqlAgentStateStore(DataSource dataSource) throws SQLException {
        String database = configuredDatabase;
        if (database == null || database.isBlank()) {
            // 未显式配置库名时，取数据源当前库（因为默认库名 agentscope 未必存在 / 有权限）
            try (Connection conn = dataSource.getConnection()) {
                database = conn.getCatalog();
            }
        }
        log.info("初始化 MysqlAgentStateStore: database={}, table={}, autoCreateTable={}",
                database, stateTable, autoCreateTable);
        return new MysqlAgentStateStore(dataSource, database, stateTable, autoCreateTable);
    }

    @Bean
    public ReActAgent reActAgent(ModelCreator modelCreator, MysqlAgentStateStore mysqlAgentStateStore,
                                  MChatMessageFileManager messageFileManager) {
        List<MiddlewareBase> middlewareBases = Lists.newArrayList();
        middlewareBases.add(new FileGenMiddleware(messageFileManager));
        return ReActAgent.builder()
                .model(modelCreator.streamModel())
                .sysPrompt("你是一个聪明可爱的智能机器人，可以回答用户的各种问题！你的名字叫青梧。")
                .name("qing wu")
                .middlewares(middlewareBases)
                // 接入 MySQL 会话状态持久化：Agent 会按 RuntimeContext 的 userId/sessionId 自动读写状态
                .stateStore(mysqlAgentStateStore)
                .build();
    }

    @Bean
    public HarnessAgent harnessAgent(ModelCreator modelCreator, MysqlAgentStateStore mysqlAgentStateStore,
                                     MChatMessageFileManager messageFileManager) {
        List<MiddlewareBase> middlewareBases = Lists.newArrayList();
        middlewareBases.add(new FileGenMiddleware(messageFileManager));
        return HarnessAgent.builder()
                .model(modelCreator.streamModel())
                .sysPrompt("你是一个学术渊博的智能机器人，可以回答用户的各种问题！你的名字叫青梧。")
                .name("qing wu")
                .middlewares(middlewareBases)
                .stateStore(mysqlAgentStateStore)
                .build();
    }
}
