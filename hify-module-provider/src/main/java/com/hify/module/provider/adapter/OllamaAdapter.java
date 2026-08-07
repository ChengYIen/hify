package com.hify.module.provider.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.http.LlmHttpClient;
import com.hify.module.provider.adapter.dto.ChatRequest;
import com.hify.module.provider.adapter.dto.ChatResponse;
import com.hify.module.provider.repository.entity.AuthConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ollama 适配器.
 *
 * <p>端点：GET /api/tags，POST /api/chat，无需认证。
 * 消息格式与 OpenAI 接近，但响应不含 usage（Token 统计在 eval_count 字段）。
 * 不支持 function calling（Ollama 工具支持因模型而异，适配器层面不做限制）。</p>
 */
public class OllamaAdapter extends AbstractProviderAdapter {

    private static final String OLLAMA_DEFAULT = "http://localhost:11434";

    public OllamaAdapter(LlmHttpClient llmHttpClient, ObjectMapper objectMapper) {
        super(llmHttpClient, objectMapper);
    }

    // ================================================================
    // 原有：连通性 & 模型发现
    // ================================================================

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
        return Collections.emptyMap();
    }

    // ================================================================
    // 新增：对话调用
    // ================================================================

    @Override
    protected String getChatEndpoint() {
        return "/api/chat";
    }

    @Override
    protected Map<String, String> buildChatHeaders(String apiKey) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        return headers;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected String buildChatRequestBody(ChatRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", request.getModel());

        // 消息列表
        List<Map<String, Object>> messages = new ArrayList<>();
        for (ChatRequest.Message msg : request.getMessages()) {
            Map<String, Object> m = new HashMap<>();
            m.put("role", msg.getRole());
            m.put("content", msg.getContent());
            messages.add(m);
        }
        body.put("messages", messages);

        body.put("stream", request.isStream());

        // 参数放在 options 子对象中
        Map<String, Object> options = new HashMap<>();
        if (request.getTemperature() != null) {
            options.put("temperature", request.getTemperature());
        }
        if (request.getMaxTokens() != null) {
            options.put("num_predict", request.getMaxTokens());
        }
        if (!options.isEmpty()) {
            body.put("options", options);
        }

        // 透传参数
        if (request.getExtra() != null) {
            body.putAll(request.getExtra());
        }

        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("序列化 Ollama 请求体失败", e);
        }
    }

    @Override
    protected ChatResponse parseChatResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            // 文本内容
            JsonNode message = root.get("message");
            String content = message != null && message.has("content")
                    ? message.get("content").asText() : "";

            // 停止原因（Ollama 用 done_reason）
            String finishReason = root.has("done_reason")
                    ? mapDoneReason(root.get("done_reason").asText()) : "stop";

            // Token 用量（Ollama 用 eval_count / prompt_eval_count）
            ChatResponse.TokenUsage tokenUsage = null;
            if (root.has("prompt_eval_count") || root.has("eval_count")) {
                int promptTokens = root.has("prompt_eval_count") ? root.get("prompt_eval_count").asInt() : 0;
                int completionTokens = root.has("eval_count") ? root.get("eval_count").asInt() : 0;
                tokenUsage = ChatResponse.TokenUsage.builder()
                        .promptTokens(promptTokens)
                        .completionTokens(completionTokens)
                        .totalTokens(promptTokens + completionTokens)
                        .build();
            }

            String model = root.has("model") ? root.get("model").asText() : null;

            return ChatResponse.builder()
                    .content(content)
                    .model(model)
                    .finishReason(finishReason)
                    .tokenUsage(tokenUsage)
                    .build();

        } catch (JsonProcessingException e) {
            throw new RuntimeException("解析 Ollama 响应失败", e);
        }
    }

    @Override
    protected String extractStreamDelta(String line) {
        try {
            JsonNode root = objectMapper.readTree(line);
            // Ollama 流式每帧可能没有 message（如首帧元数据）
            JsonNode message = root.get("message");
            if (message == null || !message.has("content")) {
                return null;
            }
            JsonNode contentNode = message.get("content");
            return contentNode.isNull() ? null : contentNode.asText();
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /**
     * 将 Ollama done_reason 映射为统一停止原因.
     * stop → stop, load → error, unload → stop
     */
    private String mapDoneReason(String doneReason) {
        return switch (doneReason) {
            case "stop" -> "stop";
            case "load" -> "error";
            case "unload" -> "stop";
            default -> doneReason;
        };
    }
}
