package com.hify.module.provider.adapter;

import com.hify.module.provider.adapter.dto.ChatRequest;
import com.hify.module.provider.adapter.dto.ChatResponse;
import com.hify.module.provider.controller.dto.ConnectionTestResult;
import com.hify.module.provider.repository.entity.Provider;

import java.util.List;

/**
 * 模型提供商适配器接口 —— 策略模式核心.
 *
 * <p>每种 LLM 厂商（OpenAI、Anthropic、Ollama 等）对应一个实现类，
 * 封装该厂商的 API 端点、认证方式、响应解析等差异。
 * 新增厂商只需实现本接口 + 注册到 {@link ProviderAdapterFactory}，零改动现有代码。</p>
 *
 * <h3>与旧 switch 方式对比</h3>
 * <pre>
 * // 旧：switch (providerCode) { case "openai": ... case "claude": ... }
 * // 新：
 * ProviderAdapter adapter = factory.getAdapter(provider.getProviderCode());
 * ConnectionTestResult result = adapter.testConnection(provider);
 * </pre>
 */
public interface ProviderAdapter {

    // ================================================================
    // 连通性 & 模型发现
    // ================================================================

    /**
     * 测试提供商连通性.
     *
     * <p>向提供商 API 的模型列表端点发起 GET 请求，验证网络可达性和认证凭据有效性。
     * 所有异常都在适配器内部消化，统一转为 {@link ConnectionTestResult}。</p>
     *
     * @param provider 提供商实体（含 baseUrl、authConfig）
     * @return 连通性测试结果（success + latencyMs + modelCount + errorMessage）
     */
    ConnectionTestResult testConnection(Provider provider);

    /**
     * 获取提供商所有可用模型.
     *
     * <p>调用提供商 API 的模型列表端点，解析并返回模型标识符列表。
     * 用于 AUTO 发现模式的模型同步。</p>
     *
     * @param provider 提供商实体
     * @return 模型标识符列表（如 ["gpt-4o", "gpt-4-turbo"]）
     */
    List<String> listModels(Provider provider);

    // ================================================================
    // 对话调用
    // ================================================================

    /**
     * 同步发送对话请求，返回完整响应.
     *
     * <p>适配器内部负责：解析 baseUrl → 构建厂商请求体 → HTTP POST → 解析响应。
     * 调用方只需构造 {@link ChatRequest}，无需感知不同厂商的 API 差异。</p>
     *
     * @param request 统一请求体（含传输层配置和业务参数）
     * @return 统一响应体
     * @throws com.hify.common.http.LlmApiException 网络/认证/限流等异常
     */
    ChatResponse chat(ChatRequest request);

    /**
     * 流式发送对话请求（SSE），通过回调逐块返回增量内容.
     *
     * <p>方法立即返回，不阻塞调用线程。适配器内部通过
     * {@link com.hify.common.http.LlmHttpClient#stream} 异步读取 SSE 流，
     * 解析后将增量文本回调给 {@link StreamChatCallback#onContent}，
     * 流结束时回调 {@link StreamChatCallback#onComplete}。</p>
     *
     * @param request  统一请求体
     * @param callback 流式回调
     */
    void streamChat(ChatRequest request, StreamChatCallback callback);
}
