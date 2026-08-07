package com.hify.module.provider.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.module.provider.controller.dto.ProviderModelCreateRequest;
import com.hify.module.provider.controller.dto.ProviderModelResponse;
import com.hify.module.provider.controller.dto.ProviderModelUpdateRequest;
import com.hify.module.provider.repository.ProviderModelMapper;
import com.hify.module.provider.repository.ProviderMapper;
import com.hify.module.provider.repository.entity.ModelConfig;
import com.hify.module.provider.service.ProviderModelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 模型配置业务实现.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderModelServiceImpl implements ProviderModelService {

    private final ProviderModelMapper providerModelMapper;
    private final ProviderMapper providerMapper;

    @Override
    public IPage<ProviderModelResponse> page(int page, int pageSize) {
        Page<ModelConfig> p = new Page<>(page, pageSize);
        Page<ModelConfig> result = providerModelMapper.selectPage(p,
                new LambdaQueryWrapper<ModelConfig>()
                        .orderByDesc(ModelConfig::getPriority)
                        .orderByDesc(ModelConfig::getId));
        return result.convert(this::toResponse);
    }

    @Override
    public ProviderModelResponse getById(Long id) {
        ModelConfig entity = providerModelMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "模型不存在: id=" + id);
        }
        return toResponse(entity);
    }

    @Override
    public List<ProviderModelResponse> listByProviderId(Long providerId) {
        if (providerMapper.selectById(providerId) == null) {
            throw new BizException(ErrorCode.PROVIDER_NOT_FOUND, "id=" + providerId);
        }
        List<ModelConfig> list = providerModelMapper.selectList(
                new LambdaQueryWrapper<ModelConfig>()
                        .eq(ModelConfig::getProviderId, providerId)
                        .orderByDesc(ModelConfig::getPriority));
        return list.stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProviderModelResponse create(ProviderModelCreateRequest request) {
        if (providerMapper.selectById(request.getProviderId()) == null) {
            throw new BizException(ErrorCode.PROVIDER_NOT_FOUND, "id=" + request.getProviderId());
        }
        ModelConfig entity = new ModelConfig();
        entity.setProviderId(request.getProviderId());
        entity.setModelName(request.getModelName());
        entity.setDisplayName(request.getDisplayName());
        entity.setModelType(request.getModelType() != null ? request.getModelType() : "LLM");
        entity.setContextWindow(request.getContextWindow() != null ? request.getContextWindow() : 8192);
        entity.setMaxOutput(request.getMaxOutput() != null ? request.getMaxOutput() : 4096);
        entity.setSupportsVision(request.getSupportsVision() != null ? request.getSupportsVision() : 0);
        entity.setSupportsTools(request.getSupportsTools() != null ? request.getSupportsTools() : 0);
        entity.setSupportsStreaming(request.getSupportsStreaming() != null ? request.getSupportsStreaming() : 1);
        entity.setPriority(request.getPriority() != null ? request.getPriority() : 0);
        entity.setFallbackModelId(request.getFallbackModelId());
        entity.setStatus("ENABLED");
        providerModelMapper.insert(entity);
        log.info("ProviderModel 创建成功: id={}, modelName={}", entity.getId(), entity.getModelName());
        return toResponse(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProviderModelResponse update(Long id, ProviderModelUpdateRequest request) {
        ModelConfig entity = providerModelMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "模型不存在: id=" + id);
        }
        if (request.getDisplayName() != null) {
            entity.setDisplayName(request.getDisplayName());
        }
        if (request.getContextWindow() != null) {
            entity.setContextWindow(request.getContextWindow());
        }
        if (request.getMaxOutput() != null) {
            entity.setMaxOutput(request.getMaxOutput());
        }
        if (request.getSupportsVision() != null) {
            entity.setSupportsVision(request.getSupportsVision());
        }
        if (request.getSupportsTools() != null) {
            entity.setSupportsTools(request.getSupportsTools());
        }
        if (request.getSupportsStreaming() != null) {
            entity.setSupportsStreaming(request.getSupportsStreaming());
        }
        if (request.getPriority() != null) {
            entity.setPriority(request.getPriority());
        }
        if (request.getFallbackModelId() != null) {
            entity.setFallbackModelId(request.getFallbackModelId());
        }
        if (request.getStatus() != null) {
            entity.setStatus(request.getStatus());
        }
        providerModelMapper.updateById(entity);
        log.info("ProviderModel 更新成功: id={}", id);
        return toResponse(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ModelConfig entity = providerModelMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "模型不存在: id=" + id);
        }
        providerModelMapper.deleteById(id);
        log.info("ProviderModel 删除成功: id={}", id);
    }

    private ProviderModelResponse toResponse(ModelConfig entity) {
        return ProviderModelResponse.builder()
                .id(entity.getId())
                .providerId(entity.getProviderId())
                .modelName(entity.getModelName())
                .displayName(entity.getDisplayName())
                .modelType(entity.getModelType())
                .contextWindow(entity.getContextWindow())
                .maxOutput(entity.getMaxOutput())
                .supportsVision(entity.getSupportsVision())
                .supportsTools(entity.getSupportsTools())
                .supportsStreaming(entity.getSupportsStreaming())
                .priority(entity.getPriority())
                .fallbackModelId(entity.getFallbackModelId())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
