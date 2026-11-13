package com.moli.agent.scope.harness.agent;

import com.moli.agent.scope.common.dao.manager.MChatMessageFileManager;
import com.moli.agent.scope.harness.middleware.DynamicModelMiddleware;
import com.moli.agent.scope.harness.middleware.FileGenMiddleware;
import com.moli.agent.scope.harness.middleware.StopOnAllDeniedMiddleware;
import com.moli.agent.scope.harness.model.ModelCreator;
import com.moli.agent.scope.harness.tools.WeatherTool;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.permission.PermissionBehavior;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.core.permission.PermissionRule;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.agentscope.extensions.mysql.state.MysqlAgentStateStore;
import io.agentscope.harness.agent.HarnessAgent;
import io.modelcontextprotocol.spec.McpSchema;
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
        // 动态模型：装配期用 llm.* 默认模型，运行期按请求选定的模型替换
        middlewareBases.add(new DynamicModelMiddleware(modelCreator));
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
        middlewareBases.add(new StopOnAllDeniedMiddleware());
        // 动态模型：使前端切换的模型名在本次调用的每次模型请求中生效
        middlewareBases.add(new DynamicModelMiddleware(modelCreator));

        // 人在回路门禁：这些工具每次被调用都暂停等用户确认。
        // 两个要点（均按 agentscope-core 2.0.4 源码核实）：
        // 一、必须至少往 context 里放一条规则，否则 PermissionContextState.isTrivial() 为 true，
        //     ReActAgent 走「只调工具自检」的轻量分支，注解工具的自检恒为 PASSTHROUGH，等于完全放行；
        // 二、注解式工具的 matchRule 未被覆写（ToolBase 默认只有 ruleContent==null 才算命中），
        //     所以这里不能写带内容的规则（如命令前缀、路径前缀），只能整工具级一律 ASK。
        PermissionContextState.Builder permCtxBuilder =
                PermissionContextState.builder()
                        .mode(PermissionMode.DEFAULT)
                        .addAllowRule(WeatherTool.TOOL_NAME, allowCall(WeatherTool.TOOL_NAME))
                        .addAllowRule("web_search", allowCall("web_search"))
                        .addAllowRule("web_fetch", allowCall("web_fetch"));
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new WeatherTool());
        //MCP：header 用链式 .header() 追加，不要 Map.of() 再 put——Map.of() 返回不可变集合会抛 UnsupportedOperationException
        /*McpClientWrapper search =
                McpClientBuilder.create("mcp_industry_chain_search")
                        .sseTransport("http://ecss-mcp-test.gf.com.cn/server/mcp/smartdata-mcp-server/sse")
                        .header("iresearchauth",
                                "Bearer eyJhbGciOiJIUzI1NiJ9.eyJzc29BY2Nlc3NUb2tlbiI6IjQwODhkOGRjLTlkNjktNDAyYi04ZmJlLWJmM2UwNjMyZTgwZCIsInNzb1JlZnJlc2hUb2tlbiI6ImJkMWVmZDVlLTc2MjUtNDBjZS1iOWUyLWI1OGZmMmZmOGI1YyIsInN0YXR1c0Rlc2MiOiLlnKjogYwiLCJuYW1lIjoi6buE5pWP6ImvIiwic3lzQWRtaW4iOnRydWUsInRva2VuVHlwZSI6ImFjY2Vzc190b2tlbiIsInVzZXJOYW1lIjoiaHVhbmdtaW5saWFuZyIsImV4cCI6MTc5NDgxNTc0Nywic3VwZXJBZG1pbiI6dHJ1ZSwidXNlcklkIjoxODksImlhdCI6MTc5Mzk1MTc0Nywic3RhdHVzIjoxfQ.EYP01ej9oo40BWhiRIHTkNZjq4KNa_KLd0USdqIbDHQ")
                        .header("Authorization", "Bearer 1efee036-a033-4569-9d75-590a293ac24e")
                        .buildAsync()
                        .block();
        toolkit.registerMcpClient(search).block();
        List<McpSchema.Tool> mcpTools = search.listTools().block();
        if (mcpTools != null) {
            mcpTools.forEach(tool -> permCtxBuilder.addAllowRule(tool.name(), allowCall(tool.name())));
            log.info("MCP [{}] 已注册并放行工具：{}", search.getName(),
                    mcpTools.stream().map(McpSchema.Tool::name).toList());
        }*/
        PermissionContextState permCtx = permCtxBuilder.build();
        return HarnessAgent.builder()
                .model(modelCreator.streamModel())
                .sysPrompt("你是一个学术渊博的智能机器人，可以回答用户的各种问题！你的名字叫青梧。")
                .name("qing wu")
                .middlewares(middlewareBases)
                .permissionContext(permCtx)
                .toolkit(toolkit)
                .stateStore(mysqlAgentStateStore)
                .build();
    }

    /**
     * 构造一条「该工具每次调用都要用户确认」的 ASK 规则。
     * <p>ruleContent 必须为 null：非 null 时会交给 {@code ToolBase.matchRule} 判定，而注解式工具没有
     * 覆写过它（默认只在 ruleContent==null 时返回 true），带内容的规则永远匹配不上。
     */
    private static PermissionRule askEveryCall(String toolName) {
        return new PermissionRule(toolName, null, PermissionBehavior.ASK, "policy");
    }

    private static PermissionRule allowCall(String toolName) {
        return new PermissionRule(toolName, null, PermissionBehavior.ALLOW, "userSettings");
    }
}
