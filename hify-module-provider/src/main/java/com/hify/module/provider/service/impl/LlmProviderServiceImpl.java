package com.hify.module.provider.service.impl;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.http.LlmApiException;
import com.hify.common.resilience.CircuitBreakerService;
import com.hify.module.provider.adapter.ProviderAdapter;
import com.hify.module.provider.adapter.ProviderAdapterFactory;
import com.hify.module.provider.adapter.StreamChatCallback;
import com.hify.module.provider.adapter.dto.ChatRequest;
import com.hify.module.provider.adapter.dto.ChatResponse;
import com.hify.module.provider.adapter.dto.EmbeddingRequest;
import com.hify.module.provider.adapter.dto.EmbeddingResponse;
import com.hify.module.provider.repository.ProviderMapper;
import com.hify.module.provider.repository.ProviderModelMapper;
import com.hify.module.provider.repository.entity.ModelConfig;
import com.hify.module.provider.repository.entity.Provider;
import com.hify.shared.llm.LlmProviderApi;
import com.hify.shared.llm.LlmStreamCallback;
import com.hify.shared.llm.LlmStreamHandle;
import com.hify.shared.llm.dto.EmbeddingResponseDTO;
import com.hify.shared.llm.dto.LlmRequestDTO;
import com.hify.shared.llm.dto.LlmResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.logstash.logback.marker.LogstashMarker;
import okhttp3.Call;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

import static net.logstash.logback.marker.Markers.append;

/**
 * LLM 提供商统一调用实现 —— 单一路由器.
 *
 * <p>实现 {@link LlmProviderApi}，按 {@code modelId} 解析出厂商、适配器与密钥，
 * 全部在 provider 模块内部完成，调用方（conversation / agent）不感知厂商差异，
 * 也不接触 apiKey/baseUrl。</p>
 *
 * <h3>解析链路</h3>
 * <pre>
 * modelId → ModelConfig（校验 ENABLED）→ Provider（校验 ENABLED）
 *         → ProviderAdapterFactory.getAdapter(providerCode) → 实际 HTTP 调用
 * </pre>
 *
 * <p>同步 {@link #chat} 走熔断 + 重试；流式 {@link #streamChat} 不做重试
 * （已发出的增量无法重放），仅透传错误。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmProviderServiceImpl implements LlmProviderApi {

    private final ProviderModelMapper providerModelMapper;
    private final ProviderMapper providerMapper;
    private final ProviderAdapterFactory adapterFactory;
    private final CircuitBreakerService circuitBreakerService;

    // ================================================================
    // LlmProviderApi
    // ================================================================

    @Override
    public LlmResponseDTO chat(LlmRequestDTO request) {
        ResolvedTarget target = resolveTarget(request.getModelId());
        ChatRequest chatRequest = toChatRequest(target, request, false);

        log.info(llmMarker(request.getModelId(), target),
                "action=llm_call_start modelId={} providerId={} provider={} modelName={} stream=false",
                request.getModelId(), target.providerId(), target.providerCode(), target.modelName());
        long start = System.currentTimeMillis();
        ChatResponse response = circuitBreakerService.executeWithResilience(
                target.providerCode(),
                () -> target.adapter().chat(chatRequest));
        long latency = System.currentTimeMillis() - start;

        LlmResponseDTO dto = toResponse(response, target);
        dto.setLatencyMs(dto.getLatencyMs() != null ? dto.getLatencyMs() : latency);
        log.info(llmMarker(request.getModelId(), target)
                        .and(append("durationMs", dto.getLatencyMs()))
                        .and(append("success", true))
                        .and(append("finishReason", dto.getFinishReason()))
                        .and(append("tokens", totalTokens(dto.getUsage()))),
                "action=llm_call_done modelId={} providerId={} provider={} modelName={} durationMs={} success=true finishReason={} tokens={}",
                request.getModelId(), target.providerId(), target.providerCode(), target.modelName(),
                dto.getLatencyMs(), dto.getFinishReason(), totalTokens(dto.getUsage()));
        return dto;
    }

    @Override
    public LlmStreamHandle streamChat(LlmRequestDTO request, LlmStreamCallback callback) {
        ResolvedTarget target = resolveTarget(request.getModelId());
        ChatRequest chatRequest = toChatRequest(target, request, true);

        log.info(llmMarker(request.getModelId(), target),
                "action=llm_call_start modelId={} providerId={} provider={} modelName={} stream=true",
                request.getModelId(), target.providerId(), target.providerCode(), target.modelName());
        Call call = target.adapter().streamChat(chatRequest, new StreamChatCallback() {
            @Override
            public void onContent(String delta) {
                callback.onContent(delta);
            }

            @Override
            public void onComplete(ChatResponse response) {
                log.info(llmMarker(request.getModelId(), target)
                                .and(append("durationMs", response.getLatencyMs()))
                                .and(append("success", true))
                                .and(append("finishReason", response.getFinishReason()))
                                .and(append("tokens", totalTokens(response.getTokenUsage()))),
                        "action=llm_call_done modelId={} providerId={} provider={} modelName={} durationMs={} success=true finishReason={} tokens={}",
                        request.getModelId(), target.providerId(), target.providerCode(), target.modelName(),
                        response.getLatencyMs(), response.getFinishReason(), totalTokens(response.getTokenUsage()));
                callback.onComplete(toResponse(response, target));
            }

            @Override
            public void onError(LlmApiException e) {
                log.warn(llmMarker(request.getModelId(), target)
                                .and(append("success", false))
                                .and(append("errorType", e.getType()))
                                .and(append("statusCode", e.getStatusCode())),
                        "action=llm_call_error modelId={} providerId={} provider={} modelName={} success=false errorType={} statusCode={}",
                        request.getModelId(), target.providerId(), target.providerCode(), target.modelName(),
                        e.getType(), e.getStatusCode());
                callback.onError(formatError(e));
            }
        });
        // 返回取消句柄：调用方在客户端断开 / 超时时 cancel() 底层 OkHttp 请求
        return call != null ? call::cancel : () -> {};
    }

    @Override
    public EmbeddingResponseDTO embed(Long modelId, List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "texts 不能为空");
        }
        ResolvedTarget target = resolveTarget(modelId);
        EmbeddingRequest request = EmbeddingRequest.builder()
                .baseUrl(target.baseUrl())
                .apiKey(target.apiKey())
                .model(target.modelName())
                .inputs(texts)
                .dimensions(target.embeddingDimensions())
                .build();

        long start = System.currentTimeMillis();
        EmbeddingResponse response = circuitBreakerService.executeWithResilience(
                target.providerCode(),
                () -> target.adapter().embed(request));
        long latency = System.currentTimeMillis() - start;

        LlmResponseDTO.TokenUsage usage = response.getPromptTokens() != null
                ? LlmResponseDTO.TokenUsage.builder()
                        .promptTokens(response.getPromptTokens())
                        .totalTokens(response.getPromptTokens())
                        .build()
                : null;

        log.info(llmMarker(modelId, target)
                        .and(append("durationMs", latency))
                        .and(append("success", true))
                        .and(append("textCount", texts.size())),
                "action=embedding_done modelId={} providerId={} provider={} modelName={} durationMs={} success=true textCount={}",
                modelId, target.providerId(), target.providerCode(), target.modelName(), latency, texts.size());
        return EmbeddingResponseDTO.builder()
                .model(response.getModel() != null ? response.getModel() : target.modelName())
                .embeddings(response.getEmbeddings())
                .usage(usage)
                .latencyMs(latency)
                .build();
    }

    // ================================================================
    // 模型解析
    // ================================================================

    /**
     * 按 modelId 解析调用目标：适配器 + 厂商编码 + 模型名 + baseUrl + apiKey.
     *
     * @throws BizException modelId 为空 / 模型不存在 / 模型或提供商禁用 / 适配器不支持
     */
    private ResolvedTarget resolveTarget(Long modelId) {
        if (modelId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "modelId 不能为空");
        }
        ModelConfig modelConfig = providerModelMapper.selectById(modelId);
        if (modelConfig == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "模型不存在: modelId=" + modelId);
        }
        if (!"ENABLED".equals(modelConfig.getStatus())) {
            throw new BizException(ErrorCode.PROVIDER_DISABLED, "模型已禁用: modelId=" + modelId);
        }

        Provider provider = providerMapper.selectById(modelConfig.getProviderId());
        if (provider == null) {
            throw new BizException(ErrorCode.PROVIDER_NOT_FOUND, "providerId=" + modelConfig.getProviderId());
        }
        if (!"ENABLED".equals(provider.getStatus())) {
            throw new BizException(ErrorCode.PROVIDER_DISABLED, "提供商已禁用: id=" + provider.getId());
        }

        ProviderAdapter adapter = adapterFactory.getAdapter(provider.getProviderCode());
        if (adapter == null) {
            throw new BizException(ErrorCode.PROVIDER_CONFIG_ERROR,
                    "不支持的提供商编码: " + provider.getProviderCode());
        }

        String apiKey = provider.getAuthConfig() != null ? provider.getAuthConfig().getApiKey() : null;
        Integer embeddingDimensions = modelConfig.getExtraParams() != null
                ? modelConfig.getExtraParams().getEmbeddingDimensions()
                : null;
        return new ResolvedTarget(adapter, provider.getProviderCode(), provider.getId(),
                modelConfig.getModelName(), provider.getBaseUrl(), apiKey, embeddingDimensions);
    }

    // ================================================================
    // DTO 映射
    // ================================================================

    private ChatRequest toChatRequest(ResolvedTarget target, LlmRequestDTO request, boolean stream) {
        List<ChatRequest.Message> messages = request.getMessages() == null ? List.of()
                : request.getMessages().stream()
                        .map(m -> ChatRequest.Message.builder()
                                .role(m.getRole())
                                .content(m.getContent())
                                .toolCallId(m.getToolCallId())
                                .toolCalls(toChatToolCalls(m.getToolCalls()))
                                .build())
                        .collect(Collectors.toList());
        List<ChatRequest.ToolDefinition> tools = request.getTools() == null ? null
                : request.getTools().stream()
                        .map(t -> ChatRequest.ToolDefinition.builder()
                                .type(t.getType())
                                .function(toChatFunction(t.getFunction()))
                                .build())
                        .collect(Collectors.toList());

        return ChatRequest.builder()
                .baseUrl(target.baseUrl())
                .apiKey(target.apiKey())
                .model(target.modelName())
                .messages(messages)
                .temperature(request.getTemperature())
                .maxTokens(request.getMaxTokens())
                .stream(stream)
                .tools(tools)
                .extra(request.getExtra())
                .build();
    }

    private List<ChatRequest.ToolCall> toChatToolCalls(
            List<LlmRequestDTO.ToolCall> toolCalls) {
        if (toolCalls == null) {
            return null;
        }
        return toolCalls.stream()
                .map(tc -> ChatRequest.ToolCall.builder()
                        .id(tc.getId())
                        .type(tc.getType())
                        .function(tc.getFunction() == null ? null
                                : ChatRequest.ToolCall.Function.builder()
                                        .name(tc.getFunction().getName())
                                        .arguments(tc.getFunction().getArguments())
                                        .build())
                        .build())
                .collect(Collectors.toList());
    }

    private ChatRequest.Function toChatFunction(LlmRequestDTO.Function function) {
        if (function == null) {
            return null;
        }
        return ChatRequest.Function.builder()
                .name(function.getName())
                .description(function.getDescription())
                .parameters(function.getParameters())
                .build();
    }

    private LlmResponseDTO toResponse(ChatResponse response, ResolvedTarget target) {
        return LlmResponseDTO.builder()
                .content(response.getContent())
                .model(response.getModel() != null ? response.getModel() : target.modelName())
                .providerId(target.providerId())
                .usage(toUsage(response.getTokenUsage()))
                .finishReason(response.getFinishReason())
                .toolCalls(response.getToolCalls())
                .latencyMs(response.getLatencyMs())
                .fallback(false)
                .build();
    }

    private LlmResponseDTO.TokenUsage toUsage(ChatResponse.TokenUsage usage) {
        if (usage == null) {
            return null;
        }
        return LlmResponseDTO.TokenUsage.builder()
                .promptTokens(usage.getPromptTokens())
                .completionTokens(usage.getCompletionTokens())
                .totalTokens(usage.getTotalTokens())
                .build();
    }

    private Integer totalTokens(LlmResponseDTO.TokenUsage usage) {
        return usage != null ? usage.getTotalTokens() : null;
    }

    private Integer totalTokens(ChatResponse.TokenUsage usage) {
        return usage != null ? usage.getTotalTokens() : null;
    }

    private LogstashMarker llmMarker(Long modelId, ResolvedTarget target) {
        return append("modelId", modelId)
                .and(append("providerId", target.providerId()))
                .and(append("provider", target.providerCode()))
                .and(append("modelName", target.modelName()));
    }

    /**
     * 将 {@link LlmApiException} 转译为面向用户的错误描述（镜像适配器的 formatError）.
     */
    private String formatError(LlmApiException e) {
        return switch (e.getType()) {
            case TIMEOUT       -> "连接超时: 请检查网络或 baseUrl 是否正确";
            case AUTH_FAILED   -> "认证失败 (401): API Key 无效或已过期";
            case NETWORK_ERROR -> "网络不可达: 请检查 baseUrl 和网络连接";
            case SERVER_ERROR  -> "服务器错误 (" + e.getStatusCode() + "): 提供商服务异常，请稍后重试";
            case RATE_LIMITED  -> "请求过于频繁 (429): 请稍后重试";
        };
    }

    /** 一次调用解析出的目标对象 */
    private record ResolvedTarget(ProviderAdapter adapter, String providerCode, Long providerId,
                                  String modelName, String baseUrl, String apiKey,
                                  Integer embeddingDimensions) {
    }
}
