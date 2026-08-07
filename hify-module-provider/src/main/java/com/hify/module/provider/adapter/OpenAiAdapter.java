package com.hify.module.provider.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.http.LlmHttpClient;
import com.hify.module.provider.adapter.dto.ChatRequest;
import com.hify.module.provider.adapter.dto.ChatResponse;
import com.hify.module.provider.repository.entity.AuthConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 适配器.
 *
 * <p>端点：GET /v1/models，POST /v1/chat/completions，认证：Bearer Token（Authorization 头）。
 * {@link OpenAiCompatibleAdapter} 继承此类，只覆写默认 Base URL。</p>
 */
public class OpenAiAdapter extends AbstractProviderAdapter {

    private static final String OPENAI_DEFAULT = "https://api.openai.com";

    public OpenAiAdapter(LlmHttpClient llmHttpClient, ObjectMapper objectMapper) {
        super(llmHttpClient, objectMapper);
    }

    // ================================================================
    // 原有：连通性 & 模型发现
    // ================================================================

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

    // ================================================================
    // 新增：对话调用
    // ================================================================

    @Override
    protected String getChatEndpoint() {
        return "/v1/chat/completions";
    }

    @Override
    protected Map<String, String> buildChatHeaders(String apiKey) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        if (apiKey != null && !apiKey.isBlank()) {
            headers.put("Authorization", "Bearer " + apiKey);
        }
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
            if (msg.getToolCallId() != null) {
                m.put("tool_call_id", msg.getToolCallId());
            }
            messages.add(m);
        }
        body.put("messages", messages);

        if (request.getTemperature() != null) {
            body.put("temperature", request.getTemperature());
        }
        if (request.getMaxTokens() != null) {
            body.put("max_tokens", request.getMaxTokens());
        }

        // 工具定义
        if (request.getTools() != null && !request.getTools().isEmpty()) {
            List<Map<String, Object>> tools = new ArrayList<>();
            for (ChatRequest.ToolDefinition tool : request.getTools()) {
                Map<String, Object> t = new HashMap<>();
                t.put("type", tool.getType() != null ? tool.getType() : "function");
                if (tool.getFunction() != null) {
                    Map<String, Object> f = new HashMap<>();
                    f.put("name", tool.getFunction().getName());
                    if (tool.getFunction().getDescription() != null) {
                        f.put("description", tool.getFunction().getDescription());
                    }
                    if (tool.getFunction().getParameters() != null) {
                        f.put("parameters", tool.getFunction().getParameters());
                    }
                    t.put("function", f);
                }
                tools.add(t);
            }
            body.put("tools", tools);
        }

        // 流式标志
        body.put("stream", request.isStream());

        // 透传参数
        if (request.getExtra() != null) {
            body.putAll(request.getExtra());
        }

        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("序列化 OpenAI 请求体失败", e);
        }
    }

    @Override
    protected ChatResponse parseChatResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.get("choices");
            JsonNode firstChoice = choices != null && choices.isArray() && choices.size() > 0
                    ? choices.get(0) : null;
            JsonNode message = firstChoice != null ? firstChoice.get("message") : null;

            // 文本内容
            String content = message != null && message.has("content")
                    ? message.get("content").asText() : "";

            // 停止原因
            String finishReason = firstChoice != null && firstChoice.has("finish_reason")
                    ? firstChoice.get("finish_reason").asText() : null;

            // Token 用量
            ChatResponse.TokenUsage tokenUsage = null;
            JsonNode usage = root.get("usage");
            if (usage != null) {
                tokenUsage = ChatResponse.TokenUsage.builder()
                        .promptTokens(usage.has("prompt_tokens") ? usage.get("prompt_tokens").asInt() : 0)
                        .completionTokens(usage.has("completion_tokens") ? usage.get("completion_tokens").asInt() : 0)
                        .totalTokens(usage.has("total_tokens") ? usage.get("total_tokens").asInt() : 0)
                        .build();
            }

            // 工具调用
            String toolCalls = null;
            if (message != null && message.has("tool_calls")) {
                toolCalls = message.get("tool_calls").toString();
            }

            // 模型名
            String model = root.has("model") ? root.get("model").asText() : null;

            return ChatResponse.builder()
                    .content(content)
                    .model(model)
                    .finishReason(finishReason)
                    .tokenUsage(tokenUsage)
                    .toolCalls(toolCalls)
                    .build();

        } catch (JsonProcessingException e) {
            throw new RuntimeException("解析 OpenAI 响应失败", e);
        }
    }

    @Override
    protected String extractStreamDelta(String line) {
        try {
            JsonNode root = objectMapper.readTree(line);
            JsonNode choices = root.get("choices");
            if (choices == null || !choices.isArray() || choices.size() == 0) {
                return null;
            }
            JsonNode delta = choices.get(0).get("delta");
            if (delta == null || !delta.has("content")) {
                return null;
            }
            JsonNode contentNode = delta.get("content");
            return contentNode.isNull() ? null : contentNode.asText();
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
