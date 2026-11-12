package com.moli.agent.scope.controller;

import com.moli.agent.scope.common.base.ApiResult;
import com.moli.agent.scope.common.dao.entity.MLlmModelConfig;
import com.moli.agent.scope.common.dao.manager.MLlmModelConfigManager;
import com.moli.agent.scope.common.vo.LlmModelOptionVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;

/**
 * 可选模型查询接口。
 * <p>前端聊天框的模型下拉框由此渲染；发送对话时把所选项的 {@code modelName} 回传，
 * 服务端据此动态切换实际调用的模型（见 DynamicModelMiddleware）。
 * <p>只返回展示字段：baseUrl / apiKey 等接入信息不下发前端。
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/llm")
public class LlmModelController {

    private final MLlmModelConfigManager llmModelConfigManager;

    /**
     * 查询启用中的模型列表（默认模型优先，其次排序号倒序）。
     * <p>配置表未初始化等异常时返回空列表，前端保持默认模型即可，不影响对话。
     */
    @GetMapping("/models")
    public ApiResult<List<LlmModelOptionVo>> models() {
        List<MLlmModelConfig> configs;
        try {
            configs = llmModelConfigManager.listEnabled();
        } catch (Exception e) {
            log.warn("查询模型列表失败（返回空列表，前端将使用服务端默认模型）: {}", e.getMessage());
            return ApiResult.ok(Collections.emptyList());
        }
        List<LlmModelOptionVo> options = configs.stream()
                .map(config -> LlmModelOptionVo.builder()
                        .modelName(config.getModelName())
                        .displayName(config.getDisplayName())
                        .vendorCode(config.getVendorCode())
                        .vendorName(config.getVendorName())
                        .isDefault(config.getIsDefault())
                        .build())
                .toList();
        return ApiResult.ok(options);
    }
}
