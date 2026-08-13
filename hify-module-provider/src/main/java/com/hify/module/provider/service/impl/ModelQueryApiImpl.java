package com.hify.module.provider.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hify.module.provider.repository.ProviderMapper;
import com.hify.module.provider.repository.ProviderModelMapper;
import com.hify.module.provider.repository.entity.ModelConfig;
import com.hify.module.provider.repository.entity.Provider;
import com.hify.shared.provider.ModelQueryApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 模型查询接口实现（供 Agent / Conversation 等模块跨模块调用）.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelQueryApiImpl implements ModelQueryApi {

    private final ProviderModelMapper providerModelMapper;
    private final ProviderMapper providerMapper;

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

    @Override
    public Long getFirstEnabledModelId() {
        // 按 ID 升序取启用的模型，过滤掉所属提供商已禁用的，返回第一个可用的
        List<ModelConfig> models = providerModelMapper.selectList(
                new LambdaQueryWrapper<ModelConfig>()
                        .eq(ModelConfig::getStatus, "ENABLED")
                        .orderByAsc(ModelConfig::getId));
        for (ModelConfig model : models) {
            Provider provider = providerMapper.selectById(model.getProviderId());
            if (provider != null && "ENABLED".equals(provider.getStatus())) {
                return model.getId();
            }
        }
        log.warn("未找到可用模型（模型启用且提供商启用）: 启用模型数={}", models.size());
        return null;
    }

    @Override
    public Long getFirstEnabledEmbeddingModelId() {
        List<ModelConfig> models = providerModelMapper.selectList(
                new LambdaQueryWrapper<ModelConfig>()
                        .eq(ModelConfig::getStatus, "ENABLED")
                        .eq(ModelConfig::getModelType, "EMBEDDING")
                        .orderByAsc(ModelConfig::getId));
        for (ModelConfig model : models) {
            Provider provider = providerMapper.selectById(model.getProviderId());
            if (provider != null && "ENABLED".equals(provider.getStatus())) {
                return model.getId();
            }
        }
        log.warn("未找到可用的 Embedding 模型（modelType=EMBEDDING 且提供商启用）: 候选数={}", models.size());
        return null;
    }
}
