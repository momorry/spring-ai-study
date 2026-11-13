package com.moli.agent.scope.harness.tools;

import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 磁盘目录读取工具。
 * <p>不继承 {@code ToolBase}：用 {@link Tool} 注解声明，由 {@code Toolkit#registerTool(Object)}
 * 反射注册。SDK 内部把注解方法包装成 {@code ReflectiveFunctionTool}（它自己继承 ToolBase），
 * 因此本工具能进入 {@code PermissionEngine} 的评估链路 —— 权限门禁全部由装配期声明的
 * ASK 规则承担（见 {@code ReActAgentConfig}），工具自身不再做任何拦截判断，只干业务。
 *
 * @author moli
 */
@Slf4j
@Component
public class WeatherTool {

    /**
     * 注册给模型的工具名，同时是 ASK 规则的 key，两处必须一致
     */
    public static final String TOOL_NAME = "query_city_weather";

    /**
     * 列出目标目录下的文件名。
     * <p>返回值直接是业务类型即可：{@code DefaultToolResultConverter} 会把它序列化成 JSON 文本；
     * 是否执行由框架的权限门禁决定（ASK ⇒ 暂停等用户在界面上确认），这里不再重复设卡。
     *
     * @param city 城市
     * @return 城市的天气
     */
    @Tool(
            name = TOOL_NAME,
            description = "查询城市的天气",
            readOnly = true)
    public Mono<ToolResultBlock> queryCityWeather(
            @ToolParam(name = "city", description = "城市") String city) {
        log.info("正在查询{}的天气", city);
        ToolResultBlock build = ToolResultBlock.builder().output(TextBlock.builder()
                .text(city + "天气是晴朗，温度24摄氏度").build()).build();
        return Mono.just(build);
    }
}
