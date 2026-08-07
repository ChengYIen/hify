package com.hify.shared.llm;

import com.hify.shared.llm.dto.LlmRequestDTO;
import com.hify.shared.llm.dto.LlmResponseDTO;

/**
 * LLM 提供商统一调用接口.
 * <p>
 * 每个 LLM 厂商（OpenAI / Claude / Gemini / Ollama）在 provider 模块中实现此接口。
 * 业务模块（conversation / agent）只依赖此接口，不感知底层是哪个厂商。
 * </p>
 *
 * <h3>实现约定</h3>
 * <ul>
 *   <li>实现类必须标注 {@code @Service} + 厂商名（如 {@code openaiLlmProvider}）</li>
 *   <li>实现类加 {@code @CircuitBreaker(name = "openai")} + {@code @Retryable}</li>
 *   <li>流式响应（SSE）由实现类内部处理，此接口只定义同步调用</li>
 * </ul>
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * // 按名称注入，按模型路由
 * @Qualifier("openaiLlmProvider")
 * private final LlmProviderApi openaiProvider;
 * }</pre>
 */
public interface LlmProviderApi {

    /**
     * 同步发送对话请求，返回完整响应.
     *
     * @param request 统一请求体
     * @param apiKey  API 密钥（从数据库配置读取，不作为 Bean 配置）
     * @return 统一响应体
     */
    LlmResponseDTO chat(LlmRequestDTO request, String apiKey);

    /**
     * 检查 API Key 是否有效.
     * <p>
     * 用于 Provider 管理页面校验用户输入的 Key。
     * </p>
     *
     * @param apiKey API 密钥
     * @return true 有效
     */
    boolean validateApiKey(String apiKey);

    /**
     * 厂商标识（如 "openai" / "claude" / "gemini"）.
     * <p>
     * 用于日志打点和错误追踪。
     * </p>
     */
    String getProviderName();
}
