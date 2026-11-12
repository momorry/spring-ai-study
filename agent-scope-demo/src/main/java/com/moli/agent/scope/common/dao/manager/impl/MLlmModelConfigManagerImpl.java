package com.moli.agent.scope.common.dao.manager.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.moli.agent.scope.common.dao.entity.MLlmModelConfig;
import com.moli.agent.scope.common.dao.manager.MLlmModelConfigManager;
import com.moli.agent.scope.common.dao.mapper.MLlmModelConfigMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 * 模型厂商配置表 服务实现类
 * </p>
 *
 * @author moli
 * @since 2026-11-10
 */
@Slf4j
@Service
public class MLlmModelConfigManagerImpl extends ServiceImpl<MLlmModelConfigMapper, MLlmModelConfig>
        implements MLlmModelConfigManager {

    @Override
    public List<MLlmModelConfig> listEnabled() {
        return lambdaQuery()
                .eq(MLlmModelConfig::getEnabled, true)
                .orderByDesc(MLlmModelConfig::getIsDefault)
                .orderByDesc(MLlmModelConfig::getSortNo)
                .orderByAsc(MLlmModelConfig::getId)
                .list();
    }

    @Override
    public MLlmModelConfig getEnabledByModelName(String modelName) {
        if (StringUtils.isBlank(modelName)) {
            return null;
        }
        return lambdaQuery()
                .eq(MLlmModelConfig::getModelName, modelName.trim())
                .eq(MLlmModelConfig::getEnabled, true)
                .last("limit 1")
                .one();
    }

    @Override
    public MLlmModelConfig getDefaultModel() {
        MLlmModelConfig defaultConfig = lambdaQuery()
                .eq(MLlmModelConfig::getEnabled, true)
                .eq(MLlmModelConfig::getIsDefault, true)
                .orderByAsc(MLlmModelConfig::getId)
                .last("limit 1")
                .one();
        if (defaultConfig != null) {
            return defaultConfig;
        }
        // 未显式标记默认行时，取排序最前的一条，保证下拉默认值稳定
        List<MLlmModelConfig> enabled = listEnabled();
        return enabled.isEmpty() ? null : enabled.get(0);
    }
}
