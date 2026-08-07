package com.hify.common.http;

import lombok.Getter;

/**
 * LLM HTTP 调用异常.
 * <p>
 * 将底层 HTTP 错误（超时、认证失败、限流、网络故障、服务端错误）
 * 统一转译为语义明确的异常类型，方便上层熔断/重试逻辑按类型决策。
 * </p>
 */
@Getter
public class LlmApiException extends RuntimeException {

    /** 错误分类 */
    public enum Type {
        /** 连接超时或读取超时 */
        TIMEOUT,
        /** 认证失败（HTTP 401），API Key 无效或过期 */
        AUTH_FAILED,
        /** 被限流（HTTP 429），需等待后重试 */
        RATE_LIMITED,
        /** 网络故障（DNS 解析失败、连接被拒、TLS 握手失败） */
        NETWORK_ERROR,
        /** 服务端错误（HTTP 5xx） */
        SERVER_ERROR
    }

    /** 错误类型 */
    private final Type type;

    /** HTTP 状态码（无响应时返回 0） */
    private final int statusCode;

    /** 请求 URL */
    private final String url;

    // ----------------------------------------------------------------
    // 构造器
    // ----------------------------------------------------------------

    public LlmApiException(Type type, int statusCode, String url) {
        super(String.format("[%s] %s (status=%d)", type, url, statusCode));
        this.type = type;
        this.statusCode = statusCode;
        this.url = url;
    }

    public LlmApiException(Type type, String url, Throwable cause) {
        super(String.format("[%s] %s: %s", type, url, cause.getMessage()), cause);
        this.type = type;
        this.statusCode = 0;
        this.url = url;
    }

    public LlmApiException(Type type, int statusCode, String url, String detail) {
        super(String.format("[%s] %s (status=%d): %s", type, url, statusCode, detail));
        this.type = type;
        this.statusCode = statusCode;
        this.url = url;
    }

    // ----------------------------------------------------------------
    // 便捷判断方法（供上层熔断/重试逻辑使用）
    // ----------------------------------------------------------------

    /**
     * 是否应该重试.
     * 限流、服务端错误、网络瞬时故障 → 重试；认证失败、超时 → 不重试。
     */
    public boolean shouldRetry() {
        return type == Type.RATE_LIMITED
                || type == Type.SERVER_ERROR
                || type == Type.NETWORK_ERROR;
    }

    /**
     * 是否应该触发熔断.
     * 只有网络故障和服务端错误才计入熔断统计窗口，限流和超时不触发熔断。
     */
    public boolean shouldCountForCircuitBreaker() {
        return type == Type.NETWORK_ERROR
                || type == Type.SERVER_ERROR;
    }
}
