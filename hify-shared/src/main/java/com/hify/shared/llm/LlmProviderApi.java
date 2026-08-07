package com.hify.shared.llm;

import com.hify.shared.llm.dto.LlmRequestDTO;
import com.hify.shared.llm.dto.LlmResponseDTO;

/**
 * LLM 提供商统一调用接口.
 * <p>
 * 由 provider 模块的单一路由实现类实现。调用方（conversation / agent）只依赖
 * 此接口，通过 {@code modelId} 指定模型，不感知底层是哪个厂商，也不接触 apiKey/baseUrl。
 * </p>
 *
 * <h3>模型路由</h3>
 * <ul>
 *   <li>调用方在 {@link LlmRequestDTO#getModelId()} 传入模型配置 ID</li>
 *   <li>provider 模块内部解析 modelId → 厂商 → 适配器 → 密钥，完成实际 HTTP 调用</li>
 *   <li>供应商差异、密钥解密、降级全部封装在 provider 模块内</li>
 * </ul>
 */
public interface LlmProviderApi {

    /**
     * 同步发送对话请求，返回完整响应.
     *
     * @param request 统一请求体（含 modelId、消息列表、采样参数）
     * @return 统一响应体
     */
    LlmResponseDTO chat(LlmRequestDTO request);

    /**
     * 流式发送对话请求（SSE），通过回调逐块返回增量.
     *
     * <p>方法可能立即返回（底层异步），增量文本通过 {@link LlmStreamCallback#onContent}
     * 实时回调，流结束时回调 {@link LlmStreamCallback#onComplete}。</p>
     *
     * @param request  统一请求体（含 modelId、消息列表、采样参数）
     * @param callback 流式回调
     */
    void streamChat(LlmRequestDTO request, LlmStreamCallback callback);
}
