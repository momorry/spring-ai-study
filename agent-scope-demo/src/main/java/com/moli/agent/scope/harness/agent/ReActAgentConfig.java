package com.moli.agent.scope.harness.agent;

import com.moli.agent.scope.common.dao.manager.MChatArtifactManager;
import com.moli.agent.scope.harness.artifact.ChatArtifactDeliveryTarget;
import com.moli.agent.scope.harness.middleware.ArtifactBindMiddleware;
import com.moli.agent.scope.harness.middleware.DynamicModelMiddleware;
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
import io.agentscope.harness.agent.filesystem.spec.LocalFilesystemSpec;
import io.agentscope.harness.agent.workspace.LocalFsMode;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.utils.Lists;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

@Slf4j
@Configuration
public class ReActAgentConfig {

    /**
     * 工作区根目录的系统属性名。与 SDK 内部同名常量一致：HarnessAgent#resolveDefaultWorkspace() 是
     * 包级私有，装配期拿不到它，只能按同样的优先级自己解析一遍，好让下面显式传给 builder 的
     * workspace 与 project 永远指同一处，不至于各算各的。
     */
    private static final String WORKSPACE_PROPERTY = "agentscope.workspace";

    /** 工作区根目录的环境变量名（优先级次于 {@link #WORKSPACE_PROPERTY}） */
    private static final String WORKSPACE_ENV = "AGENTSCOPE_WORKSPACE";

    /**
     * 工作区内充当「项目层」的子目录名。
     * <p>LocalFilesystemSpec 的 project 缺省是 {@code ${user.dir}}（IDE 里跑就是仓库根），而 overlay 的
     * 下层是以 project 为根只读解析的，等于模型一句 {@code read_file} 就能读到源码和 yml 里的库密码。
     * 本项目在仓库根和工作区根都没有任何项目层模板（AGENTS.md / skills/ / knowledge/ / tools.json 都不存在），
     * 所以把 project 指到一个空的专用子目录，让上下两层都关在工作区里。
     */
    private static final String PROJECT_DIR_NAME = "_templates";

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
                                  MChatArtifactManager artifactManager) {
        List<MiddlewareBase> middlewareBases = Lists.newArrayList();
        middlewareBases.add(new ArtifactBindMiddleware(artifactManager));
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
                                     MChatArtifactManager artifactManager,
                                     ChatArtifactDeliveryTarget artifactDeliveryTarget) {
        List<MiddlewareBase> middlewareBases = Lists.newArrayList();
        middlewareBases.add(new ArtifactBindMiddleware(artifactManager));
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
        Path workspace = resolveWorkspace();
        return HarnessAgent.builder()
                .model(modelCreator.streamModel())
                .sysPrompt("你是一个学术渊博的智能机器人，可以回答用户的各种问题！你的名字叫青梧。")
                .name("qing wu")
                .middlewares(middlewareBases)
                .permissionContext(permCtx)
                .toolkit(toolkit)
                .stateStore(mysqlAgentStateStore)
                // ---- 工作区收口：模型写文件只能落在 {workspace}/{userId}/ 下面 ----
                // 不配 .filesystem() 时 SDK 走 new LocalFilesystemSpec() 的默认值：mode=ROOTED、
                // project=${user.dir}，而 ROOTED 的 PathPolicy 恒包含 project，于是
                // write_file D:\...\pixel-jump.html 被当成「允许的绝对路径」直接写进仓库根，用户永远拿不到。
                // mode=SANDBOXED 才是真正的 chroot：绝对路径先剥掉盘符/根再锚回工作区，`..` 与 `~` 直接抛
                // SecurityException。至于靠 ROOTED 来「拒绝绝对路径」是行不通的：resolveRooted 在查 PathPolicy
                // 之前先有一条 normalized.startsWith(cwd) 短路，工作区内部的绝对路径（包括别人的 {userId}/ 目录）
                // 会绕过按用户加的前缀，跨租户读写就是这么漏的；而 SANDBOXED 下这类路径被重锚成
                // {workspace}/<原路径去掉盘符>，落不进任何人的目录。
                // 把 workspace 也显式传进去，是为了让下面 project 的推导跟 SDK 自己解析的结果必然一致。
                .workspace(workspace)
                // project 指到工作区内的空子目录，避免 overlay 下层把整个仓库（含源码与配置里的口令）暴露给 read_file
                .filesystem(new LocalFilesystemSpec()
                        .mode(LocalFsMode.SANDBOXED)
                        .project(ensureProjectDir(workspace)))
                // 产物交付出口：配了它，框架才会注册 deliver_artifact 工具（并要求未关 filesystem 工具）。
                // 不配的话模型用 write_file 生成的文件只留在服务端工作区里，用户根本拿不到。
                // 与 stateStore / filesystem 三条轴互相独立：产物上 OSS 不需要换会话状态存储，
                // 也不需要 RemoteFilesystemSpec（那个是给多副本共享工作区/长期记忆用的，
                // 且它的默认后端是 UNRESTRICTED，绝对路径原样透传，拦不住上面那个问题）。
                // deliver_artifact 是在同一次请求、同一个实例内把工作区里的字节搬进存储的，
                // 所以产物上 OSS 不存在跨副本需求，不必让文件字节再进一遍数据库。
                .artifactDeliveryTarget(artifactDeliveryTarget)
                .build();
    }

    /**
     * 解析 Agent 工作区根目录，优先级与 SDK 内部一致：系统属性 {@code agentscope.workspace} →
     * 环境变量 {@code AGENTSCOPE_WORKSPACE} → {@code ${user.dir}/.agentscope/workspace}。
     * <p>这里必须自己解析一遍而不是留给 SDK 缺省，是因为 {@code project} 要按工作区推导；
     * 两者分开算就会出现“filesystem 锁的根”和“agent 实际用的根”不是同一个目录。
     */
    private static Path resolveWorkspace() {
        String property = System.getProperty(WORKSPACE_PROPERTY);
        if (property != null && !property.isBlank()) {
            return Paths.get(property.strip());
        }
        String env = System.getenv(WORKSPACE_ENV);
        if (env != null && !env.isBlank()) {
            return Paths.get(env.strip());
        }
        return Paths.get(System.getProperty("user.dir")).resolve(".agentscope/workspace");
    }

    /**
     * 保证项目层目录存在并返回它。建不了目录不拦启动：这层只负责“读到模板”，缺了就是读不到，
     * 不影响写入（写入走上层工作区），为它抛异常等于把一个无关紧要的模板目录变成服务起不来。
     */
    private static Path ensureProjectDir(Path workspace) {
        Path project = workspace.resolve(PROJECT_DIR_NAME);
        try {
            Files.createDirectories(project);
            log.info("Agent 工作区已收口: workspace={}, project={}", workspace, project);
        } catch (IOException | RuntimeException e) {
            log.warn("项目层目录创建失败（不影响对话与产物交付）: {}", project, e);
        }
        return project;
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
