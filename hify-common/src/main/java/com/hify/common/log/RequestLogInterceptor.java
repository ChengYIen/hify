package com.hify.common.log;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

/**
 * 请求日志拦截器 —— 记录每个请求的 method、path、status、耗时.
 * <p>
 * 慢请求（&gt;1s）标 WARN，正常请求标 INFO。
 * 请求进入时生成 traceId 放入 MDC，请求结束时清理。
 * </p>
 * <p>
 * <b>与 {@code TraceIdFilter} 协作：</b>
 * {@code TraceIdFilter}（Servlet Filter，优先级最高）会先设置 traceId 到 MDC。
 * 本拦截器检查 MDC 中是否已有 traceId，有则复用，无则生成，避免重复。
 * cleanup 时同样只清理自己生成的 traceId。
 * </p>
 *
 * <h3>日志输出示例</h3>
 * <pre>
 * INFO  GET /api/v1/agents → 200 (42ms)
 * WARN  POST /api/v1/conversations/chat → 200 (2340ms) [SLOW]
 * </pre>
 */
@Slf4j
@Component
public class RequestLogInterceptor implements HandlerInterceptor {

    /** 与 {@code TraceIdFilter.TRACE_ID_KEY} 保持一致 */
    static final String TRACE_ID_KEY = "traceId";

    /** request attribute：请求开始时间 */
    private static final String START_TIME_ATTR = RequestLogInterceptor.class.getName() + ".startTime";

    /** request attribute：是否由本拦截器生成的 traceId */
    private static final String TRACE_GENERATED_ATTR = RequestLogInterceptor.class.getName() + ".traceGenerated";

    /** 慢请求阈值（毫秒） */
    private static final long SLOW_THRESHOLD_MS = 1000;

    // ----------------------------------------------------------------
    // preHandle
    // ----------------------------------------------------------------

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {
        // traceId：MDC 中已有（由 TraceIdFilter 设置）则复用，否则生成
        String traceId = MDC.get(TRACE_ID_KEY);
        boolean generatedByMe = false;
        if (traceId == null || traceId.isEmpty()) {
            traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            MDC.put(TRACE_ID_KEY, traceId);
            generatedByMe = true;
        }

        request.setAttribute(START_TIME_ATTR, System.currentTimeMillis());
        request.setAttribute(TRACE_GENERATED_ATTR, generatedByMe);
        return true;
    }

    // ----------------------------------------------------------------
    // afterCompletion
    // ----------------------------------------------------------------

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex) {
        try {
            long start = (long) request.getAttribute(START_TIME_ATTR);
            long elapsed = System.currentTimeMillis() - start;
            int status = response.getStatus();
            String method = request.getMethod();
            String path = request.getRequestURI();

            if (elapsed > SLOW_THRESHOLD_MS) {
                log.warn("{} {} → {} ({}ms) [SLOW]", method, path, status, elapsed);
            } else {
                log.info("{} {} → {} ({}ms)", method, path, status, elapsed);
            }
        } finally {
            // 仅清理本拦截器生成的 traceId，避免影响 TraceIdFilter 的后续清理
            if (Boolean.TRUE.equals(request.getAttribute(TRACE_GENERATED_ATTR))) {
                MDC.remove(TRACE_ID_KEY);
            }
        }
    }
}
