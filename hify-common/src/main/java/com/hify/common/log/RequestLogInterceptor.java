package com.hify.common.log;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 请求日志拦截器 —— 记录每个请求的 method、path、status、耗时.
 * <p>
 * 对话链路关键节点之一：<b>请求进入 / 请求结束</b>。
 * 慢请求（&gt;1s）标 WARN，正常请求标 INFO。
 * </p>
 * <p>
 * <b>不再管理 traceId。</b>traceId 由 OpenTelemetry 生成并写入 MDC
 * （见 {@code TraceIdFilter} 类注释），本拦截器只负责日志内容本身。
 * 这样避免了两套 ID 互相覆盖 —— 原先「MDC 没有就自己生成 UUID」的逻辑会在
 * OTel span 之外产生第二个 traceId，导致同一请求的日志被切成两段。
 * </p>
 *
 * <h3>日志输出示例（JSON 由 logback 编码器产出，此处示意字段）</h3>
 * <pre>
 * INFO  请求进入: GET /api/v1/agents
 * INFO  请求完成: GET /api/v1/agents → 200 (42ms)
 * WARN  请求完成: POST /api/v1/chat/messages → 200 (2340ms) [SLOW]
 * </pre>
 */
@Slf4j
@Component
public class RequestLogInterceptor implements HandlerInterceptor {

    /** request attribute：请求开始时间 */
    private static final String START_TIME_ATTR = RequestLogInterceptor.class.getName() + ".startTime";

    /** 慢请求阈值（毫秒） */
    private static final long SLOW_THRESHOLD_MS = 1000;

    // ----------------------------------------------------------------
    // preHandle — 关键节点：请求进入
    // ----------------------------------------------------------------

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {
        request.setAttribute(START_TIME_ATTR, System.currentTimeMillis());
        log.info("请求进入: {} {}", request.getMethod(), request.getRequestURI());
        return true;
    }

    // ----------------------------------------------------------------
    // afterCompletion — 关键节点：请求结束
    // ----------------------------------------------------------------

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex) {
        Object startAttr = request.getAttribute(START_TIME_ATTR);
        if (startAttr == null) {
            // preHandle 未执行（前置拦截器已中断请求），无耗时可算
            return;
        }
        long elapsed = System.currentTimeMillis() - (long) startAttr;
        int status = response.getStatus();
        String method = request.getMethod();
        String path = request.getRequestURI();

        if (elapsed > SLOW_THRESHOLD_MS) {
            log.warn("请求完成: {} {} → {} ({}ms) [SLOW]", method, path, status, elapsed);
        } else {
            log.info("请求完成: {} {} → {} ({}ms)", method, path, status, elapsed);
        }
    }
}
