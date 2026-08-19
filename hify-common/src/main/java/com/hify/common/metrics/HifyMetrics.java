package com.hify.common.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Hify 业务指标统一入口.
 *
 * <p>业务类只调用语义化方法，不直接拼接指标名或注册 Meter。
 * Counter 名称不手动追加 {@code _total}，由 Prometheus registry 自动导出。</p>
 */
@Slf4j
@Component
public class HifyMetrics {

    private static final String CONVERSATION_REQUEST_COUNT = "hify_conversation_request_count";
    private static final String CONVERSATION_REQUEST_DURATION = "hify_conversation_request_duration";
    private static final String LLM_CALL_COUNT = "hify_llm_call_count";
    private static final String LLM_CALL_DURATION = "hify_llm_call_duration";
    private static final String MCP_TOOL_CALL_COUNT = "hify_mcp_tool_call_count";
    private static final String MCP_TOOL_CALL_DURATION = "hify_mcp_tool_call_duration";
    private static final String CIRCUIT_BREAKER_STATE = "hify_circuit_breaker_state";
    private static final String COMPONENT_HEALTH = "hify_component_health";

    private final MeterRegistry meterRegistry;

    /**
     * providerName -> CLOSED(0) / OPEN(1) / HALF_OPEN(2).
     * Gauge 只注册一次，状态变化只更新 Map 的值。
     */
    private final ConcurrentHashMap<String, Integer> circuitBreakerStates =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> circuitBreakerGauges =
            new ConcurrentHashMap<>();
    /**
     * component -> UP(1) / DOWN(0)，供 MySQL/Redis/pgvector 依赖健康告警使用。
     */
    private final ConcurrentHashMap<String, Integer> componentHealthStates =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> componentHealthGauges =
            new ConcurrentHashMap<>();

    public HifyMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public Sample conversationStarted(Long agentId) {
        counter(CONVERSATION_REQUEST_COUNT, "agentId", metricTag(agentId)).increment();
        return new Sample();
    }

    public void conversationCompleted(Sample sample, Long agentId) {
        if (!sample.tryComplete()) {
            return;
        }
        timer(CONVERSATION_REQUEST_DURATION,
                "agentId", metricTag(agentId))
                .record(sample.elapsedNanos(), java.util.concurrent.TimeUnit.NANOSECONDS);
    }

    public Sample llmCallStarted() {
        return new Sample();
    }

    public void llmCallCompleted(Sample sample, String provider, String model, String result) {
        if (!sample.tryComplete()) {
            return;
        }
        counter(LLM_CALL_COUNT,
                "provider", metricTag(provider),
                "model", metricTag(model),
                "result", result).increment();
        timer(LLM_CALL_DURATION,
                "provider", metricTag(provider),
                "model", metricTag(model),
                "result", result).record(sample.elapsedNanos(), java.util.concurrent.TimeUnit.NANOSECONDS);
    }

    public Sample mcpToolCallStarted() {
        return new Sample();
    }

    public void mcpToolCallCompleted(Sample sample, String result) {
        if (!sample.tryComplete()) {
            return;
        }
        counter(MCP_TOOL_CALL_COUNT, "result", result).increment();
        timer(MCP_TOOL_CALL_DURATION, "result", result)
                .record(sample.elapsedNanos(), java.util.concurrent.TimeUnit.NANOSECONDS);
    }

    /**
     * 更新 provider 熔断状态。每个 provider 只注册一条 Gauge。
     *
     * @param providerName provider 名称
     * @param stateCode CLOSED=0、OPEN=1、HALF_OPEN=2
     */
    public void circuitBreakerState(String providerName, int stateCode) {
        circuitBreakerStates.put(providerName, stateCode);
        circuitBreakerGauges.computeIfAbsent(providerName, this::registerCircuitBreakerGauge);
    }

    /**
     * 更新依赖组件健康状态。每个组件只注册一条 Gauge，UP=1、DOWN=0。
     */
    public void componentHealth(String component, boolean up) {
        componentHealthStates.put(component, up ? 1 : 0);
        componentHealthGauges.computeIfAbsent(component, this::registerComponentHealthGauge);
    }

    private Boolean registerCircuitBreakerGauge(String providerName) {
        try {
            Gauge.builder(CIRCUIT_BREAKER_STATE, circuitBreakerStates,
                            states -> states.getOrDefault(providerName, 0))
                    .description("Hify circuit breaker state: CLOSED=0, OPEN=1, HALF_OPEN=2")
                    .tag("provider", providerName)
                    .register(meterRegistry);
            return Boolean.TRUE;
        } catch (RuntimeException e) {
            log.warn("熔断器 Gauge 注册失败: provider={}, error={}",
                    providerName, e.getMessage());
            return null;
        }
    }

    private Boolean registerComponentHealthGauge(String component) {
        try {
            Gauge.builder(COMPONENT_HEALTH, componentHealthStates,
                            states -> states.getOrDefault(component, 0))
                    .description("Hify dependency health: UP=1, DOWN=0")
                    .tag("component", component)
                    .register(meterRegistry);
            return Boolean.TRUE;
        } catch (RuntimeException e) {
            log.warn("健康状态 Gauge 注册失败: component={}, error={}",
                    component, e.getMessage());
            return null;
        }
    }

    private Counter counter(String name, String... tags) {
        return Counter.builder(name)
                .tags(tags)
                .register(meterRegistry);
    }

    private Timer timer(String name, String... tags) {
        return Timer.builder(name)
                .tags(tags)
                .publishPercentileHistogram()
                .register(meterRegistry);
    }

    private String metricTag(Long value) {
        return value != null ? String.valueOf(value) : "none";
    }

    private String metricTag(String value) {
        return StringUtils.hasText(value) ? value : "unknown";
    }

    /**
     * 一次业务操作的单调时钟采样，避免 wall clock 调整影响延迟统计。
     */
    public static final class Sample {
        private final long startedAtNanos = System.nanoTime();
        private final AtomicBoolean completed = new AtomicBoolean(false);

        private boolean tryComplete() {
            return completed.compareAndSet(false, true);
        }

        private long elapsedNanos() {
            return System.nanoTime() - startedAtNanos;
        }

        public long elapsedMillis() {
            return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(elapsedNanos());
        }
    }
}
