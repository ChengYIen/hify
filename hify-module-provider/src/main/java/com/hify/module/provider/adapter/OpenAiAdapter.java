package com.hify.module.provider.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.http.LlmHttpClient;
import com.hify.module.provider.repository.entity.AuthConfig;

import java.util.HashMap;
import java.util.Map;

/**
 * OpenAI 适配器.
 *
 * <p>端点：GET /v1/models，认证：Bearer Token（Authorization 头）。
 * {@link OpenAiCompatibleAdapter} 继承此类，只覆写默认 Base URL。</p>
 */
public class OpenAiAdapter extends AbstractProviderAdapter {

    private static final String OPENAI_DEFAULT = "https://api.openai.com";

    public OpenAiAdapter(LlmHttpClient llmHttpClient, ObjectMapper objectMapper) {
        super(llmHttpClient, objectMapper);
    }

    @Override
    protected String getDefaultBaseUrl() {
        return OPENAI_DEFAULT;
    }

    @Override
    protected String getModelsEndpoint() {
        return "/v1/models";
    }

    @Override
    protected String getModelsArrayKey() {
        return "data";
    }

    @Override
    protected String extractModelId(JsonNode item) {
        JsonNode idNode = item.get("id");
        return idNode != null ? idNode.asText() : null;
    }

    @Override
    protected Map<String, String> buildHeaders(AuthConfig auth) {
        Map<String, String> headers = new HashMap<>();
        if (auth != null && auth.getApiKey() != null) {
            headers.put("Authorization", "Bearer " + auth.getApiKey());
        }
        return headers;
    }
}
