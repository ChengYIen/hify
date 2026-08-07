package com.hify.module.provider.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.http.LlmHttpClient;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 提供商适配器工厂.
 *
 * <p>根据 {@code providerCode} 返回对应的 {@link ProviderAdapter} 实例。
 * 新增厂商适配器时，只需在本类的 {@link #init()} 中多注册一行映射即可。</p>
 *
 * <h3>注册示例</h3>
 * <pre>
 * adapterMap.put("gemini", new GeminiAdapter(llmHttpClient, objectMapper));
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProviderAdapterFactory {

    private final LlmHttpClient llmHttpClient;
    private final ObjectMapper objectMapper;

    private Map<String, ProviderAdapter> adapterMap;

    @PostConstruct
    void init() {
        adapterMap = Map.of(
                "openai",              new OpenAiAdapter(llmHttpClient, objectMapper),
                "openai_compatible",   new OpenAiCompatibleAdapter(llmHttpClient, objectMapper),
                "claude",              new AnthropicAdapter(llmHttpClient, objectMapper),
                "ollama",              new OllamaAdapter(llmHttpClient, objectMapper)
        );
        log.info("ProviderAdapterFactory 初始化完成: 已注册 {} 个适配器", adapterMap.size());
    }

    /**
     * 根据提供商编码获取对应的适配器.
     *
     * @param providerCode 提供商编码（如 openai、claude、ollama）
     * @return 对应的适配器实例，不支持的类型返回 {@code null}
     */
    public ProviderAdapter getAdapter(String providerCode) {
        return adapterMap.get(providerCode);
    }
}
