package com.hify.module.provider.service.impl;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.module.provider.adapter.ProviderAdapter;
import com.hify.module.provider.adapter.ProviderAdapterFactory;
import com.hify.module.provider.controller.dto.ConnectionTestResult;
import com.hify.module.provider.repository.ProviderMapper;
import com.hify.module.provider.repository.entity.Provider;
import com.hify.module.provider.service.ProviderConnectivityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 提供商连通性测试业务实现.
 *
 * <p>通过 {@link ProviderAdapterFactory} 获取对应的 {@link ProviderAdapter}，
 * 将连通性测试委托给具体适配器执行。纯 IO 操作，不涉及事务。</p>
 *
 * <h3>与旧 switch 方式对比</h3>
 * <pre>
 * // 旧：switch (providerCode) { case "openai": ... case "claude": ... }
 * // 新：
 * ProviderAdapter adapter = factory.getAdapter(providerCode);
 * if (adapter == null) → unsupported;
 * return adapter.testConnection(provider);
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderConnectivityServiceImpl implements ProviderConnectivityService {

    private final ProviderMapper providerMapper;
    private final ProviderAdapterFactory factory;

    @Override
    public ConnectionTestResult testConnection(Long providerId) {
        Provider provider = providerMapper.selectById(providerId);
        if (provider == null) {
            throw new BizException(ErrorCode.PROVIDER_NOT_FOUND, "id=" + providerId);
        }

        ProviderAdapter adapter = factory.getAdapter(provider.getProviderCode());
        if (adapter == null) {
            return ConnectionTestResult.builder()
                    .success(false)
                    .latencyMs(0)
                    .modelCount(0)
                    .errorMessage("不支持的提供商类型: " + provider.getProviderCode())
                    .build();
        }

        return adapter.testConnection(provider);
    }
}
