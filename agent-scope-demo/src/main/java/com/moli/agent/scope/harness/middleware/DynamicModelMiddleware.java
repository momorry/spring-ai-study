package com.moli.agent.scope.harness.middleware;

import com.moli.agent.scope.harness.model.ModelCreator;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.model.Model;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ModelCallInput;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import reactor.core.publisher.Flux;

import java.util.function.Function;

/**
 * 动态模型路由中间件：按「本次请求选定的模型」替换模型调用输入中的模型实例。
 * <p>背景：AgentScope 的 Agent 把 {@code model} 作为 final 字段在构建期固定，
 * 且 Agent 是单例 Bean，不能按请求就地替换（并发下会互相污染）。
 * 而 {@link MiddlewareBase#onModelCall} 是官方提供的「原始模型调用」拦截点，
 * 其入参 {@link ModelCallInput} 携带本次调用真正使用的模型，出参可以被重新构造，
 * 因此无需为每个模型建一个 Agent，只要在这里把模型换掉即可。
 * <p>传递路径：Controller → {@code InConversationVo.modelName}
 * → {@code RuntimeContext.put(MODEL_NAME_KEY, name)}（每次调用独立、线程安全）
 * → 本中间件读取并按名称换取模型实例（{@link ModelCreator#resolveStreamModel(String)}）。
 * <p>降级：未携带模型名、配置缺失、构建失败等一律保持原模型（即 {@code llm.*} 默认模型），不影响对话。
 */
@Slf4j
public class DynamicModelMiddleware implements MiddlewareBase {

    /**
     * RuntimeContext 属性键：本次调用要使用的模型标识（来自前端下拉选择）。
     */
    public static final String MODEL_NAME_KEY = "llmModelName";

    private final ModelCreator modelCreator;

    public DynamicModelMiddleware(ModelCreator modelCreator) {
        this.modelCreator = modelCreator;
    }

    @Override
    public Flux<AgentEvent> onModelCall(Agent agent,
                                        RuntimeContext ctx,
                                        ModelCallInput input,
                                        Function<ModelCallInput, Flux<AgentEvent>> next) {
        Model target;
        try {
            target = resolveTarget(ctx, input);
        } catch (Exception e) {
            // 路由失败不能拖垮对话：继续使用 Agent 装配时的默认模型
            log.warn("动态模型路由失败，使用默认模型: {}", e.getMessage());
            return next.apply(input);
        }
        if (target == null || target == input.model()) {
            return next.apply(input);
        }
        log.info("本次模型调用切换为: {} (原: {})", target.getModelName(),
                input.model() == null ? "null" : input.model().getModelName());
        return next.apply(new ModelCallInput(input.messages(), input.tools(), input.options(), target));
    }

    /**
     * 从运行时上下文取模型名，并解析为模型实例；无需切换时返回原实例。
     */
    private Model resolveTarget(RuntimeContext ctx, ModelCallInput input) {
        if (ctx == null) {
            return input.model();
        }
        Object raw = ctx.get(MODEL_NAME_KEY);
        String modelName = raw == null ? null : String.valueOf(raw);
        if (StringUtils.isBlank(modelName)) {
            return input.model();
        }
        return modelCreator.resolveStreamModel(modelName);
    }
}
