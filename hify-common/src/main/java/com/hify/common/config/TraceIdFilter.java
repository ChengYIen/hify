package com.hify.common.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * 链路追踪过滤器 —— 在 MDC 中写入 traceId，实现全链路日志串联.
 * <p>
 * 请求入口检查 {@code X-Trace-Id} header，有则复用，无则生成新的 UUID。
 * 响应头也返回 {@code X-Trace-Id}，前端报错时可直接贴出。
 * </p>
 * <p>
 * {@code @Order(HIGHEST_PRECEDENCE)} 确保在所有 Filter 中最先执行。
 * </p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter implements Filter {

    /** 追踪 ID 的 MDC key，对应 logback-spring.xml pattern 中的 %X{traceId} */
    public static final String TRACE_ID_KEY = "traceId";

    /** HTTP 头名 */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    public void doFilter(ServletRequest request,
                         ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // 有则复用，无则生成
        String traceId = httpRequest.getHeader(TRACE_ID_HEADER);
        if (traceId == null || traceId.isEmpty()) {
            traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }

        try {
            MDC.put(TRACE_ID_KEY, traceId);
            httpResponse.setHeader(TRACE_ID_HEADER, traceId);
            chain.doFilter(request, response);
        } finally {
            // 防止内存泄漏
            MDC.remove(TRACE_ID_KEY);
        }
    }
}
