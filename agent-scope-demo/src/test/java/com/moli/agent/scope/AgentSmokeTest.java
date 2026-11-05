package com.moli.agent.scope;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.UserMessage;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 智能体运行冒烟测试（不依赖 Spring 容器与数据库）。
 *
 * <p>包含两个用例：
 * <ol>
 *   <li>{@link #agentShouldBeBuildable()}：仅验证 OpenAIChatModel + ReActAgent 能否正常装配，
 *       不发起任何网络请求，始终会执行。</li>
 *   <li>{@link #agentShouldStreamEvents()}：向真实大模型发一轮对话，验证智能体端到端能否运行。
 *       需要通过环境变量提供真实凭证，未配置时自动跳过（保证 mvn test 在无凭证环境也能通过）。</li>
 * </ol>
 *
 * <p>运行真实调用示例（PowerShell）：
 * <pre>
 *   $env:LLM_BASE_URL="https://dashscope.aliyuncs.com/compatible-mode/v1"
 *   $env:LLM_API_KEY="sk-xxxx"
 *   $env:LLM_MODEL="qwen-plus"
 *   mvn -pl agent-scope-demo test -Dtest=AgentSmokeTest
 * </pre>
 */
class AgentSmokeTest {

    private static final String SYS_PROMPT = "你是一个用于测试的助手，请用一句话简洁回答。";

    /**
     * 用例一：验证智能体装配（构建 model + agent），不联网，始终执行。
     */
    @Test
    @DisplayName("智能体应能被正常装配（不发起真实调用）")
    void agentShouldBeBuildable() {
        OpenAIChatModel chatModel = OpenAIChatModel.builder()
                .apiKey("test-key")
                .baseUrl("https://api.openai.com/v1")
                .modelName("gpt-4o-mini")
                .stream(true)
                .build();
        assertNotNull(chatModel, "OpenAIChatModel 构建失败");

        ReActAgent agent = ReActAgent.builder()
                .model(chatModel)
                .name("smoke-test-agent")
                .sysPrompt(SYS_PROMPT)
                .build();
        assertNotNull(agent, "ReActAgent 构建失败");

        System.out.println("[AgentSmokeTest] 智能体装配成功: " + agent.getClass().getSimpleName());
    }

    /**
     * 用例二：向真实大模型发起一轮流式对话，验证智能体端到端能否运行。
     * 未配置 LLM_API_KEY 时跳过。
     */
    @Test
    @DisplayName("智能体应能对接真实模型并返回事件流")
    void agentShouldStreamEvents() {
        String apiKey = System.getenv("LLM_API_KEY");
        Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank(),
                "未设置环境变量 LLM_API_KEY，跳过智能体真实调用测试");

        String baseUrl = envOr("LLM_BASE_URL", "https://api.openai.com/v1");
        String model = envOr("LLM_MODEL", "gpt-4o-mini");

        OpenAIChatModel chatModel = OpenAIChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(model)
                .stream(true)
                .build();

        ReActAgent agent = ReActAgent.builder()
                .model(chatModel)
                .name("smoke-test-agent")
                .sysPrompt(SYS_PROMPT)
                .build();

        UserMessage message = UserMessage.builder()
                .content(TextBlock.builder().text("你好，请用一句话介绍你自己").build())
                .build();
        RuntimeContext rc = RuntimeContext.builder()
                .sessionId("smoke-test-session")
                .userId("tester")
                .build();

        // 测试运行在主线程，可安全 block 收集全部事件
        Flux<AgentEvent> eventFlux = agent.streamEvents(message, rc);
        List<AgentEvent> events = eventFlux
                .doOnNext(e -> System.out.println("[event] " + e.getClass().getSimpleName()))
                .collectList()
                .block(Duration.ofSeconds(90));

        assertNotNull(events, "智能体未返回任何事件（返回 null）");
        assertFalse(events.isEmpty(), "智能体返回事件为空，说明未能正常运行");

        // 从结果事件中提取最终文本，进一步确认模型确实产生了回复
        String answer = events.stream()
                .filter(e -> e instanceof AgentResultEvent)
                .map(e -> ((AgentResultEvent) e).getResult())
                .filter(java.util.Objects::nonNull)
                .map(Msg::getTextContent)
                .filter(t -> t != null && !t.isBlank())
                .reduce((first, second) -> second)   // 取最后一段非空回复
                .orElse("");

        System.out.println("[AgentSmokeTest] 智能体运行成功，事件数=" + events.size() + "，回复=" + answer);
        assertFalse(answer.isBlank(), "智能体产生了事件但没有文本回复，请检查模型配置");
    }

    private static String envOr(String key, String defaultValue) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? defaultValue : v;
    }
}
