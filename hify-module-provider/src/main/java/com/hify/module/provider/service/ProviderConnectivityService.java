package com.hify.module.provider.service;

import com.hify.module.provider.controller.dto.ConnectionTestResult;

/**
 * 提供商连通性测试业务接口.
 */
public interface ProviderConnectivityService {

    /**
     * 测试指定 provider 的连通性.
     * <p>
     * 根据 providerCode 分发到不同的 API 端点：
     * <ul>
     *   <li>openai / openai_compatible → GET /v1/models（Bearer Token）</li>
     *   <li>claude → GET /v1/models（x-api-key + anthropic-version）</li>
     *   <li>ollama → GET /api/tags（无认证）</li>
     * </ul>
     *
     * @param providerId 提供商 ID
     * @return 连通性测试结果
     */
    ConnectionTestResult testConnection(Long providerId);
}
