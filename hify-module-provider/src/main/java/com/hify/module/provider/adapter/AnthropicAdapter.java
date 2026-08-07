package com.hify.module.provider.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.http.LlmHttpClient;
import com.hify.module.provider.repository.entity.AuthConfig;

import java.util.HashMap;
import java.util.Map;

/**
 * Anthropic（Claude）适配器.
 *
 * <p>端点：GET /v1/models，认证：x-api-key + anthropic-version 头。
 * 响应格式与 OpenAI 相同（{@code data} 数组，每项含 {@code id}）。</p>
 */
public class AnthropicAdapter extends AbstractProviderAdapter {

    private static final String ANTHROPIC_DEFAULT = "https://api.anthropic.com";

    public AnthropicAdapter(LlmHttpClient llmHttpClient, ObjectMapper objectMapper) {
        super(llmHttpClient, objectMapper);
    }

    @Override
    protected String getDefaultBaseUrl() {
        return ANTHROPIC_DEFAULT;
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
        if (auth != null) {
            if (auth.getApiKey() != null) {
                headers.put("x-api-key", auth.getApiKey());
            }
            if (auth.getAnthropicVersion() != null) {
                headers.put("anthropic-version", auth.getAnthropicVersion());
            }
        }
        return headers;
    }
}
