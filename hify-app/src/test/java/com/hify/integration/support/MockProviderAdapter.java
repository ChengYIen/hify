package com.hify.integration.support;

import com.hify.shared.llm.LlmProviderApi;
import com.hify.shared.llm.LlmStreamCallback;
import com.hify.shared.llm.LlmStreamHandle;
import com.hify.shared.llm.dto.EmbeddingResponseDTO;
import com.hify.shared.llm.dto.LlmRequestDTO;
import com.hify.shared.llm.dto.LlmResponseDTO;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * mock profile 的 LLM 适配器：流式返回固定增量，同步调用结束回调，
 * 让 SSE 测试不依赖真实模型 API。
 */
@Component
@Primary
@Profile("mock")
public class MockProviderAdapter implements LlmProviderApi {

    private static final String MODEL = "mock-gpt";

    private final List<LlmRequestDTO> requests = new CopyOnWriteArrayList<>();

    @Override
    public LlmResponseDTO chat(LlmRequestDTO request) {
        requests.add(request);
        return response("你好，我是客服助手。");
    }

    @Override
    public LlmStreamHandle streamChat(LlmRequestDTO request, LlmStreamCallback callback) {
        requests.add(request);
        callback.onContent("你好，");
        callback.onContent("我是客服助手。");
        callback.onComplete(response("你好，我是客服助手。"));
        return () -> {
            // no-op
        };
    }

    @Override
    public EmbeddingResponseDTO embed(Long modelId, List<String> texts) {
        return EmbeddingResponseDTO.builder()
                .model("mock-embedding")
                .embeddings(texts.stream().map(text -> List.of(0.1f)).toList())
                .build();
    }

    public List<LlmRequestDTO> getRequests() {
        return requests;
    }

    public void clearRequests() {
        requests.clear();
    }

    private LlmResponseDTO response(String content) {
        return LlmResponseDTO.builder()
                .content(content)
                .model(MODEL)
                .finishReason("stop")
                .latencyMs(1L)
                .build();
    }
}
