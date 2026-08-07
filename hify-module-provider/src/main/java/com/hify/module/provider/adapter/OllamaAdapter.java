package com.hify.module.provider.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.http.LlmHttpClient;
import com.hify.module.provider.repository.entity.AuthConfig;

import java.util.Collections;
import java.util.Map;

/**
 * Ollama 适配器.
 *
 * <p>端点：GET /api/tags，无需认证。
 * 响应 JSON 以 {@code models} 为数组 key，每项模型用 {@code name} 标识。</p>
 */
public class OllamaAdapter extends AbstractProviderAdapter {

    private static final String OLLAMA_DEFAULT = "http://localhost:11434";

    public OllamaAdapter(LlmHttpClient llmHttpClient, ObjectMapper objectMapper) {
        super(llmHttpClient, objectMapper);
    }

    @Override
    protected String getDefaultBaseUrl() {
        return OLLAMA_DEFAULT;
    }

    @Override
    protected String getModelsEndpoint() {
        return "/api/tags";
    }

    @Override
    protected String getModelsArrayKey() {
        return "models";
    }

    @Override
    protected String extractModelId(JsonNode item) {
        JsonNode nameNode = item.get("name");
        return nameNode != null ? nameNode.asText() : null;
    }

    @Override
    protected Map<String, String> buildHeaders(AuthConfig auth) {
        // Ollama 无需认证
        return Collections.emptyMap();
    }
}
