package com.hify.shared.llm;

import com.hify.shared.llm.dto.LlmResponseDTO;

/**
 * 流式 LLM 调用回调 —— shared 契约.
 *
 * <p>用于 {@link LlmProviderApi#streamChat}。回调方法只使用 JDK 类型与 shared DTO，
 * 不依赖任何模块类（hify-shared 不得引用 hify-common）。</p>
 */
public interface LlmStreamCallback {

    /**
     * 收到一段增量文本.
     *
     * @param delta 增量文本（可能为空字符串，表示心跳或空帧）
     */
    void onContent(String delta);

    /**
     * 流正常结束，携带完整响应.
     *
     * @param response 完整响应，{@code content} 为全量文本，含 usage/latencyMs
     */
    void onComplete(LlmResponseDTO response);

    /**
     * 流出错（网络/超时/限流/认证失败/服务端错误）.
     *
     * @param message 面向用户的错误描述
     */
    void onError(String message);
}
