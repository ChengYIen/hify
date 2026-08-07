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
 * Anthropic（Claude）适配器.
 *
 * <p>端点：GET /v1/models，POST /v1/messages，认证：x-api-key + anthropic-version 头。
 * 消息格式与 OpenAI 不同：system 是顶级字段，messages 不含 system 角色。</p>
 */
public class AnthropicAdapter extends AbstractProviderAdapter {

    private static final String ANTHROPIC_DEFAULT = "https://api.anthropic.com";
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    public AnthropicAdapter(LlmHttpClient llmHttpClient, ObjectMapper objectMapper) {
        super(llmHttpClient, objectMapper);
    }

    // ================================================================
    // 原有：连通性 & 模型发现
    // ================================================================

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

    // ================================================================
    // 新增：对话调用
    // ================================================================

    @Override
    protected String getChatEndpoint() {
        return "/v1/messages";
    }

    @Override
    protected Map<String, String> buildChatHeaders(String apiKey) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        if (apiKey != null && !apiKey.isBlank()) {
            headers.put("x-api-key", apiKey);
        }
        headers.put("anthropic-version", ANTHROPIC_VERSION);
        return headers;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected String buildChatRequestBody(ChatRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", request.getModel());
        body.put("max_tokens", request.getMaxTokens() != null ? request.getMaxTokens() : 4096);

        // Anthropic 的 system 是顶级字段，从 messages 中分离
        List<Map<String, Object>> conversationMessages = new ArrayList<>();
        for (ChatRequest.Message msg : request.getMessages()) {
            if ("system".equals(msg.getRole())) {
                // 多个 system 合并为一个（Anthropic 只支持单个 system prompt）
                body.put("system", msg.getContent());
            } else {
                Map<String, Object> m = new HashMap<>();
                m.put("role", msg.getRole());
                // Anthropic 用 content 数组而非字符串
                List<Map<String, Object>> contentList = new ArrayList<>();
                Map<String, Object> textBlock = new HashMap<>();
                textBlock.put("type", "text");
                textBlock.put("text", msg.getContent());
                contentList.add(textBlock);
                m.put("content", contentList);
                conversationMessages.add(m);
            }
        }
        body.put("messages", conversationMessages);

        if (request.getTemperature() != null) {
            body.put("temperature", request.getTemperature());
        }

        // 工具定义（Anthropic 格式）
        if (request.getTools() != null && !request.getTools().isEmpty()) {
            List<Map<String, Object>> tools = new ArrayList<>();
            for (ChatRequest.ToolDefinition tool : request.getTools()) {
                Map<String, Object> t = new HashMap<>();
                t.put("name", tool.getFunction().getName());
                if (tool.getFunction().getDescription() != null) {
                    t.put("description", tool.getFunction().getDescription());
                }
                t.put("input_schema", tool.getFunction().getParameters() != null
                        ? tool.getFunction().getParameters()
                        : Map.of("type", "object", "properties", Map.of()));
                tools.add(t);
            }
            body.put("tools", tools);
        }

        body.put("stream", request.isStream());

        // 透传参数
        if (request.getExtra() != null) {
            body.putAll(request.getExtra());
        }

        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("序列化 Anthropic 请求体失败", e);
        }
    }

    @Override
    protected ChatResponse parseChatResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            // 文本内容（Anthropic content 是数组）
            StringBuilder content = new StringBuilder();
            JsonNode contentArray = root.get("content");
            if (contentArray != null && contentArray.isArray()) {
                for (JsonNode block : contentArray) {
                    if ("text".equals(block.get("type").asText())) {
                        content.append(block.get("text").asText());
                    }
                }
            }

            // 停止原因
            String stopReason = root.has("stop_reason")
                    ? mapStopReason(root.get("stop_reason").asText()) : null;

            // Token 用量
            ChatResponse.TokenUsage tokenUsage = null;
            JsonNode usage = root.get("usage");
            if (usage != null) {
                tokenUsage = ChatResponse.TokenUsage.builder()
                        .promptTokens(usage.has("input_tokens") ? usage.get("input_tokens").asInt() : 0)
                        .completionTokens(usage.has("output_tokens") ? usage.get("output_tokens").asInt() : 0)
                        .totalTokens(
                                (usage.has("input_tokens") ? usage.get("input_tokens").asInt() : 0)
                                + (usage.has("output_tokens") ? usage.get("output_tokens").asInt() : 0)
                        )
                        .build();
            }

            // 工具调用
            String toolCalls = null;
            if (contentArray != null && contentArray.isArray()) {
                List<Map<String, Object>> toolUseBlocks = new ArrayList<>();
                for (JsonNode block : contentArray) {
                    if ("tool_use".equals(block.get("type").asText())) {
                        Map<String, Object> toolUse = new HashMap<>();
                        toolUse.put("id", block.get("id").asText());
                        toolUse.put("type", "function");
                        Map<String, Object> function = new HashMap<>();
                        function.put("name", block.get("name").asText());
                        function.put("arguments", block.get("input").toString());
                        toolUse.put("function", function);
                        toolUseBlocks.add(toolUse);
                    }
                }
                if (!toolUseBlocks.isEmpty()) {
                    toolCalls = objectMapper.writeValueAsString(toolUseBlocks);
                }
            }

            String model = root.has("model") ? root.get("model").asText() : null;

            return ChatResponse.builder()
                    .content(content.toString())
                    .model(model)
                    .finishReason(stopReason)
                    .tokenUsage(tokenUsage)
                    .toolCalls(toolCalls)
                    .build();

        } catch (JsonProcessingException e) {
            throw new RuntimeException("解析 Anthropic 响应失败", e);
        }
    }

    @Override
    protected String extractStreamDelta(String line) {
        try {
            JsonNode root = objectMapper.readTree(line);
            String type = root.has("type") ? root.get("type").asText() : null;

            if ("content_block_delta".equals(type)) {
                JsonNode delta = root.get("delta");
                if (delta != null && "text_delta".equals(delta.get("type").asText())) {
                    JsonNode textNode = delta.get("text");
                    return textNode != null ? textNode.asText() : null;
                }
            }
            return null;
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /**
     * 将 Anthropic 停止原因映射为统一 stop_reason.
     * end_turn → stop, max_tokens → length, tool_use → tool_calls
     */
    private String mapStopReason(String anthropicStopReason) {
        return switch (anthropicStopReason) {
            case "end_turn" -> "stop";
            case "max_tokens" -> "length";
            case "tool_use" -> "tool_calls";
            case "stop_sequence" -> "stop";
            default -> anthropicStopReason;
        };
    }
}
