package com.hify.module.provider.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.http.LlmHttpClient;

/**
 * OpenAI 兼容接口适配器.
 *
 * <p>与 {@link OpenAiAdapter} 共用完全相同的 HTTP 请求逻辑（端点、认证头、响应解析），
 * 唯一区别：无默认 Base URL，用户必须配置 {@code baseUrl}。</p>
 *
 * <p>基类 {@link AbstractProviderAdapter#testConnection} 中已处理
 * {@code resolveUrl} 返回 {@code null} 的场景（无默认 URL），直接返回失败结果。</p>
 */
public class OpenAiCompatibleAdapter extends OpenAiAdapter {

    public OpenAiCompatibleAdapter(LlmHttpClient llmHttpClient, ObjectMapper objectMapper) {
        super(llmHttpClient, objectMapper);
    }

    /**
     * 无默认 Base URL —— 用户必须配置.
     * <p>基类会处理此情况，返回连接失败结果。</p>
     */
    @Override
    protected String getDefaultBaseUrl() {
        return null;
    }
}
