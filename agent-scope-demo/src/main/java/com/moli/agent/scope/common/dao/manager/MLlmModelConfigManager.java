package com.moli.agent.scope.common.dao.manager;

import com.baomidou.mybatisplus.spring.service.IService;
import com.moli.agent.scope.common.dao.entity.MLlmModelConfig;

import java.util.List;

/**
 * <p>
 * 模型厂商配置表 服务类
 * </p>
 *
 * @author moli
 * @since 2026-11-10
 */
public interface MLlmModelConfigManager extends IService<MLlmModelConfig> {

    /**
     * 查询启用的模型配置，供前端下拉列表渲染。
     * 排序：默认模型优先，其次 sort_no 倒序、id 升序。
     *
     * @return 启用的模型配置列表（含 api_key，由调用方转换为展示 VO 后再返回前端）
     */
    List<MLlmModelConfig> listEnabled();

    /**
     * 按模型标识查询启用的模型配置（服务端动态切换模型的依据）。
     *
     * @param modelName 模型标识
     * @return 命中的配置；未启用 / 不存在时返回 null
     */
    MLlmModelConfig getEnabledByModelName(String modelName);

    /**
     * 查询启用中的默认模型配置。
     *
     * @return 默认配置；未配置时返回 null
     */
    MLlmModelConfig getDefaultModel();
}
