package com.hify.module.provider.adapter;

import com.hify.common.http.LlmApiException;
import com.hify.module.provider.adapter.dto.ChatResponse;

/**
 * 流式聊天回调.
 *
 * <p>用于 {@link ProviderAdapter#streamChat} 方法。
 * 与底层 {@link com.hify.common.http.StreamCallback} 不同，
 * 本接口返回的是适配器解析后的语义对象（内容增量 / 完整响应），而非原始 SSE 文本行。
 * 适配器内部负责将各厂商不同的 SSE 格式统一转为标准回调。</p>
 */
public interface StreamChatCallback {

    /**
     * 收到一段增量文本内容.
     *
     * @param delta 增量文本（可能为空字符串，表示心跳或空帧）
     */
    void onContent(String delta);

    /**
     * 流正常结束，携带完整的 {@link ChatResponse}（含 token 用量等元信息）.
     *
     * @param response 完整响应，{@code content} 为全量文本
     */
    void onComplete(ChatResponse response);

    /**
     * 流出错（网络断开、超时、非 200 状态码等）.
     *
     * @param e 已分类的 LLM API 异常
     */
    void onError(LlmApiException e);
}
