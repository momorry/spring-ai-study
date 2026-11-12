package com.moli.agent.scope.harness.model;

import com.moli.agent.scope.common.dao.entity.MLlmModelConfig;
import com.moli.agent.scope.common.dao.manager.MLlmModelConfigManager;
import io.agentscope.core.formatter.Formatter;
import io.agentscope.core.model.Model;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.extensions.model.openai.compat.deepseek.DeepSeekFormatter;
import io.agentscope.extensions.model.openai.compat.glm.GLMFormatter;
import io.agentscope.extensions.model.openai.compat.kimi.KimiFormatter;
import io.agentscope.extensions.model.openai.compat.minimax.MiniMaxFormatter;
import io.agentscope.extensions.model.openai.dto.OpenAIMessage;
import io.agentscope.extensions.model.openai.dto.OpenAIRequest;
import io.agentscope.extensions.model.openai.dto.OpenAIResponse;
import io.agentscope.extensions.model.openai.formatter.OpenAIChatFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模型创建器：负责产出 Agent 使用的大模型实例。
 * <p>两种来源：
 * <ol>
 *   <li><b>配置兜底</b>：{@code application-local.yml} 的 {@code llm.*}（{@link #streamModel()} / {@link #blockModel()}），
 *       用于 Agent 启动时装配的默认模型，以及数据库配置缺失时的回退；</li>
 *   <li><b>数据库动态</b>：{@link #resolveStreamModel(String)} 按模型标识读取 {@code m_llm_model_config}
 *       （供应商 / baseUrl / apiKey / modelName / formatter），一个厂商可配置多个模型。
 *       由 {@code DynamicModelMiddleware} 在每次模型调用前按请求携带的 modelName 换取实例，
 *       从而实现「单例 Agent + 按请求切换模型」。</li>
 * </ol>
 * <p>因 Agent 内部的 {@code model} 字段为 final、不可就地替换，这里以「按调用替换 ModelCallInput 中的模型」
 * 的方式实现动态化，同时用 TTL 缓存避免每轮推理都查库和重复创建 HTTP 客户端。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ModelCreator {

    /** 模型实例缓存有效期：期间同一名重复使用同一实例；配置变更最迟 1 分钟内生效 */
    private static final long CACHE_TTL_MILLIS = 60_000L;

    private final MLlmModelConfigManager llmModelConfigManager;

    /** 兜底配置（Agent 装配的默认模型） */
    @Value("${llm.base-url}")
    private String baseUrl;
    @Value("${llm.model}")
    private String model;
    @Value("${llm.api-key}")
    private String apiKey;

    /** 动态模型实例缓存：modelName -> 实例（含兜底结果，避免配置缺失时反复查库） */
    private final Map<String, CachedModel> modelCache = new ConcurrentHashMap<>();

    /** 兜底模型实例（惰性创建并复用：每个实例自带 HTTP 客户端，不宜每次调用都新建） */
    private volatile OpenAIChatModel defaultStreamModel;
    private volatile OpenAIChatModel defaultBlockModel;

    /**
     * 默认流式模型：取自 {@code llm.*} 配置。
     */
    public OpenAIChatModel streamModel() {
        OpenAIChatModel cached = defaultStreamModel;
        if (cached == null) {
            synchronized (this) {
                cached = defaultStreamModel;
                if (cached == null) {
                    cached = OpenAIChatModel.builder()
                            .apiKey(apiKey)
                            .baseUrl(baseUrl)
                            .modelName(model)
                            .stream(true)               // 流式输出
                            .build();
                    defaultStreamModel = cached;
                }
            }
        }
        return cached;
    }

    /**
     * 默认阻塞（非流式）模型：取自 {@code llm.*} 配置。
     */
    public OpenAIChatModel blockModel() {
        OpenAIChatModel cached = defaultBlockModel;
        if (cached == null) {
            synchronized (this) {
                cached = defaultBlockModel;
                if (cached == null) {
                    cached = OpenAIChatModel.builder()
                            .apiKey(apiKey)
                            .baseUrl(baseUrl)
                            .modelName(model)
                            .stream(false)
                            .build();
                    defaultBlockModel = cached;
                }
            }
        }
        return cached;
    }

    /**
     * 按模型标识解析流式模型实例（动态切换入口）。
     * <p>任何异常或配置缺失都不向上抛出，统一回退默认模型，保证对话可用。
     *
     * @param modelName 前端传入的模型标识；为空时直接返回默认模型
     * @return 本次调用应使用的模型实例
     */
    public Model resolveStreamModel(String modelName) {
        if (StringUtils.isBlank(modelName)) {
            return streamModel();
        }
        String key = modelName.trim();
        CachedModel cached = modelCache.get(key);
        if (cached != null && !cached.expired()) {
            return cached.model();
        }
        Model resolved = buildByConfig(key);
        modelCache.put(key, new CachedModel(resolved, System.currentTimeMillis() + CACHE_TTL_MILLIS));
        return resolved;
    }

    /**
     * 依据 {@code m_llm_model_config} 配置行构建模型实例；查不到 / 关键信息缺失 / 构建失败时回退默认模型。
     */
    private Model buildByConfig(String modelName) {
        MLlmModelConfig config;
        try {
            config = llmModelConfigManager.getEnabledByModelName(modelName);
        } catch (Exception e) {
            // 表未初始化等异常不影响对话：降级为默认模型
            log.warn("查询模型配置失败，回退默认模型 [{}]: {}", model, e.getMessage());
            return streamModel();
        }
        if (config == null) {
            log.warn("未找到启用中的模型配置 [{}]，回退默认模型 [{}]", modelName, model);
            return streamModel();
        }
        if (StringUtils.isBlank(config.getBaseUrl()) || StringUtils.isBlank(config.getApiKey())) {
            log.warn("模型配置 [{}] 缺少 baseUrl/apiKey，回退默认模型 [{}]", modelName, model);
            return streamModel();
        }
        try {
            OpenAIChatModel.Builder builder = OpenAIChatModel.builder()
                    .apiKey(config.getApiKey())
                    .baseUrl(config.getBaseUrl())
                    .modelName(config.getModelName())
                    .stream(true)
                    .formatter(resolveFormatter(config.getFormatter()));
            if (StringUtils.isNotBlank(config.getEndpointPath())) {
                // 部分 OpenAI 兼容网关的接口路径与标准不同（如 /api/v1/llm/chat）
                builder.endpointPath(config.getEndpointPath());
            }
            OpenAIChatModel built = builder.build();
            log.info("动态模型已就绪: vendor={}, modelName={}, baseUrl={}, formatter={}",
                    config.getVendorName(), config.getModelName(), config.getBaseUrl(), config.getFormatter());
            return built;
        } catch (Exception e) {
            log.warn("构建动态模型 [{}] 失败，回退默认模型 [{}]: {}", modelName, model, e.getMessage());
            return streamModel();
        }
    }

    /**
     * 按配置选择消息格式化器：OpenAI 兼容厂商的请求/响应细节略有差异，SDK 为各家提供了专用 formatter。
     * <p>未识别的取值一律回退标准 OpenAI 格式化器（与改造前行为一致）。
     */
    private Formatter<OpenAIMessage, OpenAIResponse, OpenAIRequest> resolveFormatter(String formatter) {
        String type = StringUtils.isBlank(formatter) ? "openai" : formatter.trim().toLowerCase(Locale.ROOT);
        return switch (type) {
            case "deepseek" -> new DeepSeekFormatter();
            case "kimi", "moonshot" -> new KimiFormatter();
            case "glm", "zhipu" -> new GLMFormatter();
            case "minimax" -> new MiniMaxFormatter();
            default -> new OpenAIChatFormatter();
        };
    }

    /**
     * 清空动态模型缓存（配置变更后调用可立即生效，无需等待 TTL 过期）。
     */
    public void clearCache() {
        modelCache.clear();
        log.info("动态模型缓存已清空");
    }

    /**
     * 缓存条目：模型实例 + 过期时间戳。
     */
    private record CachedModel(Model model, long expireAt) {
        boolean expired() {
            return System.currentTimeMillis() > expireAt;
        }
    }
}
