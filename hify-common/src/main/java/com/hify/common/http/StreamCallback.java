package com.hify.common.http;

/**
 * SSE 流式响应回调.
 * <p>
 * 用于 {@link LlmHttpClient#stream} 方法，每收到一行 SSE data 回调一次。
 * </p>
 */
public interface StreamCallback {

    /**
     * 收到一行 SSE data（去掉 {@code "data: "} 前缀后的内容）.
     * <p>
     * LLM 流式 API 的典型数据行：
     * <pre>{@code
     * {"id":"chatcmpl-xxx","choices":[{"delta":{"content":"你好"}}]}
     * [DONE]
     * }</pre>
     *
     * @param data SSE 事件数据内容，以 {@code [DONE]} 表示流结束
     */
    void onLine(String data);

    /**
     * 流正常结束（服务端关闭连接或发送 {@code [DONE]} 之后）.
     * <p>
     * 默认空实现，子接口按需覆盖。
     * </p>
     */
    default void onComplete() {
    }

    /**
     * 流发生异常（网络断开、超时、非 200 状态码等）.
     * <p>
     * 传入的异常已通过 {@link LlmApiException} 分类，
     * 调用方可通过 {@link LlmApiException#getType()} 决定是否重连。
     * </p>
     *
     * @param e 已分类的 LLM API 异常
     */
    void onError(LlmApiException e);
}
