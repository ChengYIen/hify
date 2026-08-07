package com.hify.module.provider.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hify.module.provider.repository.ProviderModelMapper;
import com.hify.module.provider.repository.entity.ModelConfig;
import com.hify.shared.provider.ModelQueryApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 模型查询接口实现（供 Agent 等模块跨模块调用）.
 */
@Service
@RequiredArgsConstructor
public class ModelQueryApiImpl implements ModelQueryApi {

    private final ProviderModelMapper providerModelMapper;

    @Override
    public boolean isModelAvailable(Long modelId) {
        if (modelId == null) {
            return false;
        }
        return providerModelMapper.exists(
                new LambdaQueryWrapper<ModelConfig>()
                        .eq(ModelConfig::getId, modelId)
                        .eq(ModelConfig::getStatus, "ENABLED"));
    }
}
