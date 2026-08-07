package com.hify.module.provider.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hify.common.config.CacheNames;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.module.provider.controller.dto.ProviderCreateRequest;
import com.hify.module.provider.controller.dto.ProviderHealthResponse;
import com.hify.module.provider.controller.dto.ProviderModelResponse;
import com.hify.module.provider.controller.dto.ProviderResponse;
import com.hify.module.provider.controller.dto.ProviderUpdateRequest;
import com.hify.module.provider.repository.ProviderHealthMapper;
import com.hify.module.provider.repository.ProviderMapper;
import com.hify.module.provider.repository.ProviderModelMapper;
import com.hify.module.provider.repository.entity.ModelConfig;
import com.hify.module.provider.repository.entity.Provider;
import com.hify.module.provider.repository.entity.ProviderHealth;
import com.hify.module.provider.service.ProviderModelService;
import com.hify.module.provider.service.ProviderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 模型提供商业务实现.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderServiceImpl implements ProviderService {

    private final ProviderMapper providerMapper;
    private final ProviderHealthMapper providerHealthMapper;
    private final ProviderModelMapper providerModelMapper;
    private final ProviderModelService providerModelService;

    // -------------------------------------------------------
    // 查询
    // -------------------------------------------------------

    @Override
    @Cacheable(cacheNames = CacheNames.PROVIDER, key = "'list:' + #page + ':' + #pageSize + ':' + #providerCode + ':' + #status")
    public IPage<ProviderResponse> list(int page, int pageSize, String providerCode, String status) {
        LambdaQueryWrapper<Provider> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(providerCode)) {
            wrapper.eq(Provider::getProviderCode, providerCode);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(Provider::getStatus, status.toUpperCase());
        }
        wrapper.orderByDesc(Provider::getPriority)
                .orderByDesc(Provider::getId);

        Page<Provider> p = new Page<>(page, pageSize);
        Page<Provider> result = providerMapper.selectPage(p, wrapper);

        // ---- batch enrich: model count + latest health latency ----
        List<Long> providerIds = result.getRecords().stream()
                .map(Provider::getId)
                .collect(Collectors.toList());

        Map<Long, Long> modelCountMap = Collections.emptyMap();
        Map<Long, Integer> healthLatencyMap = Collections.emptyMap();

        if (!providerIds.isEmpty()) {
            // 模型数（仅统计已启用的模型）
            List<ModelConfig> models = providerModelMapper.selectList(
                    new LambdaQueryWrapper<ModelConfig>()
                            .in(ModelConfig::getProviderId, providerIds)
                            .eq(ModelConfig::getStatus, "ENABLED"));
            modelCountMap = models.stream()
                    .collect(Collectors.groupingBy(ModelConfig::getProviderId, Collectors.counting()));

            // 最近一次健康检查延迟（按 checkedAt 倒序，取第一条）
            List<ProviderHealth> healthRecords = providerHealthMapper.selectList(
                    new LambdaQueryWrapper<ProviderHealth>()
                            .in(ProviderHealth::getProviderId, providerIds)
                            .orderByDesc(ProviderHealth::getCheckedAt));
            healthLatencyMap = healthRecords.stream()
                    .collect(Collectors.toMap(
                            ProviderHealth::getProviderId,
                            ProviderHealth::getResponseTimeMs,
                            (first, second) -> first)); // first = latest (orderByDesc)
        }

        final Map<Long, Long> finalModelCountMap = modelCountMap;
        final Map<Long, Integer> finalHealthLatencyMap = healthLatencyMap;

        return result.convert(entity -> {
            ProviderResponse resp = toResponse(entity);
            resp.setModelCount(finalModelCountMap.getOrDefault(entity.getId(), 0L).intValue());
            resp.setLastHealthResponseTimeMs(finalHealthLatencyMap.get(entity.getId()));
            return resp;
        });
    }

    @Override
    @Cacheable(cacheNames = CacheNames.PROVIDER, key = "#id")
    public ProviderResponse getById(Long id) {
        Provider entity = providerMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.PROVIDER_NOT_FOUND, "id=" + id);
        }

        // 关联模型配置列表
        List<ProviderModelResponse> modelConfigs = providerModelService.listByProviderId(id);

        // 最近一次健康检查记录
        ProviderHealth latestHealth = providerHealthMapper.selectOne(
                new LambdaQueryWrapper<ProviderHealth>()
                        .eq(ProviderHealth::getProviderId, id)
                        .orderByDesc(ProviderHealth::getCheckedAt)
                        .last("LIMIT 1"));

        ProviderResponse response = toResponse(entity);
        response.setModelConfigs(modelConfigs);
        response.setLatestHealth(latestHealth != null ? toHealthResponse(latestHealth) : null);
        return response;
    }

    // -------------------------------------------------------
    // 写操作
    // -------------------------------------------------------

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = CacheNames.PROVIDER, allEntries = true)
    public ProviderResponse create(ProviderCreateRequest request) {
        // 校验名称不重复
        Long count = providerMapper.selectCount(
                new LambdaQueryWrapper<Provider>()
                        .eq(Provider::getName, request.getName()));
        if (count > 0) {
            throw new BizException(ErrorCode.DUPLICATE, "提供商名称已存在: " + request.getName());
        }

        Provider entity = new Provider();
        entity.setName(request.getName());
        entity.setDescription(request.getDescription());
        entity.setProviderCode(request.getProviderCode());
        entity.setAuthConfig(request.getAuthConfig());
        entity.setBaseUrl(request.getBaseUrl());
        entity.setDiscoveryType(request.getDiscoveryType());
        entity.setPriority(request.getPriority() != null ? request.getPriority() : 0);
        entity.setStatus("ENABLED");
        entity.setHealthStatus("UNKNOWN");
        providerMapper.insert(entity);
        log.info("Provider 创建成功: id={}, name={}, code={}", entity.getId(), entity.getName(), entity.getProviderCode());
        return toResponse(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = CacheNames.PROVIDER, allEntries = true)
    public ProviderResponse update(Long id, ProviderUpdateRequest request) {
        Provider entity = providerMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.PROVIDER_NOT_FOUND, "id=" + id);
        }
        // 如果修改了名称，校验唯一性
        if (StringUtils.hasText(request.getName()) && !request.getName().equals(entity.getName())) {
            Long count = providerMapper.selectCount(
                    new LambdaQueryWrapper<Provider>()
                            .eq(Provider::getName, request.getName()));
            if (count > 0) {
                throw new BizException(ErrorCode.DUPLICATE, "提供商名称已存在: " + request.getName());
            }
        }
        if (request.getName() != null) {
            entity.setName(request.getName());
        }
        if (request.getDescription() != null) {
            entity.setDescription(request.getDescription());
        }
        if (request.getAuthConfig() != null) {
            entity.setAuthConfig(request.getAuthConfig());
        }
        if (request.getBaseUrl() != null) {
            entity.setBaseUrl(request.getBaseUrl());
        }
        if (request.getStatus() != null) {
            entity.setStatus(request.getStatus());
        }
        if (request.getPriority() != null) {
            entity.setPriority(request.getPriority());
        }
        providerMapper.updateById(entity);
        log.info("Provider 更新成功: id={}", id);
        return toResponse(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = CacheNames.PROVIDER, allEntries = true)
    public void delete(Long id) {
        Provider entity = providerMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.PROVIDER_NOT_FOUND, "id=" + id);
        }
        providerMapper.deleteById(id);
        log.info("Provider 删除成功: id={}", id);
    }

    // -------------------------------------------------------
    // 内部转换方法
    // -------------------------------------------------------

    private ProviderResponse toResponse(Provider entity) {
        return ProviderResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .providerCode(entity.getProviderCode())
                .baseUrl(entity.getBaseUrl())
                .status(entity.getStatus())
                .healthStatus(entity.getHealthStatus())
                .discoveryType(entity.getDiscoveryType())
                .priority(entity.getPriority())
                .lastSyncedAt(entity.getLastSyncedAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private ProviderHealthResponse toHealthResponse(ProviderHealth entity) {
        return ProviderHealthResponse.builder()
                .id(entity.getId())
                .providerId(entity.getProviderId())
                .healthStatus(entity.getHealthStatus())
                .responseTimeMs(entity.getResponseTimeMs())
                .failReason(entity.getFailReason())
                .alertTriggered(entity.getAlertTriggered())
                .checkedAt(entity.getCheckedAt())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
