package com.hify.common.config;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * traceId 响应头过滤器 —— 把当前 OpenTelemetry span 的 traceId 回写到响应头.
 * <p>
 * <b>traceId 不再由本类生成。</b>生成与 MDC 注入全部交给 OpenTelemetry：
 * <ol>
 *   <li>Spring Boot 的 {@code ServerHttpObservationFilter} 为每个请求开启一个 server span；</li>
 *   <li>Micrometer Tracing 的 OTel 桥接为该 span 生成 W3C 标准的 32 位 traceId；</li>
 *   <li>{@code Slf4JEventListener} 在 span 作用域内把 {@code traceId}/{@code spanId} 写入 MDC，
 *       logback 的 JSON 编码器直接输出这两个字段。</li>
 * </ol>
 * 本类只做一件事：把 traceId 通过 {@code X-Trace-Id} 响应头暴露给前端，
 * 用户报错时贴上这个值即可在日志里捞出完整链路。
 * </p>
 * <p>
 * <b>上游链路续传走 W3C {@code traceparent} 头</b>（OTel 默认传播格式），
 * 由传播器自动处理，不需要本类介入。原先「读 {@code X-Trace-Id} 请求头则复用」的行为
 * 已被 {@code traceparent} 取代 —— 自定义头无法携带 span 父子关系，
 * 而 {@code traceparent} 是将来接 Jaeger/Tempo 时唯一能对齐的格式。
 * </p>
 * <p>
 * {@code @Order}：必须排在 {@code ServerHttpObservationFilter}
 * （{@code HIGHEST_PRECEDENCE + 1}）之后，否则 span 尚未创建，取不到 traceId。
 * </p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TraceIdFilter implements Filter {

    /** MDC key，由 Micrometer Tracing 写入，对应 logback JSON 输出的 traceId 字段 */
    public static final String TRACE_ID_KEY = "traceId";

    /** 响应头名：前端报错时可直接贴出此值定位链路 */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    private final Tracer tracer;

    public TraceIdFilter(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public void doFilter(ServletRequest request,
                         ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {
        Span currentSpan = tracer.currentSpan();
        if (currentSpan != null && response instanceof HttpServletResponse httpResponse) {
            // 响应头必须在 chain 之前写入 —— SSE 等流式响应一旦开始输出，header 就无法再改
            httpResponse.setHeader(TRACE_ID_HEADER, currentSpan.context().traceId());
        }
        chain.doFilter(request, response);
    }
}
