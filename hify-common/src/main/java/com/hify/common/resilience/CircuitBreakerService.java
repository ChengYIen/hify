package com.hify.common.resilience;

import com.hify.common.http.LlmApiException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 熔断器服务 —— 按 providerName 管理独立的熔断 + 重试实例.
 * <p>
 * 调用链：CircuitBreaker(外层) → Retry(内层) → 实际 HTTP 调用。
 * 熔断打开时重试不会执行，避免无意义等待。
 * </p>
 *
 * <h3>重试规则</h3>
 * <ul>
 *   <li>网络超时 / ConnectException / SocketTimeoutException → 重试 2 次，间隔 1s</li>
 *   <li>限流 (429) → 重试 2 次，退避 2s → 4s</li>
 *   <li>服务端错误 (5xx) → 重试 2 次，间隔 1s</li>
 *   <li>认证失败 (401) → 不重试</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 同步调用
 * String result = circuitBreakerService.executeWithResilience("openai",
 *     () -> llmHttpClient.post(url, headers, body));
 *
 * // 仅装饰，由调用方执行
 * Supplier<String> decorated = circuitBreakerService.decorate("openai",
 *     () -> llmHttpClient.post(url, headers, body));
 * String result = decorated.get();
 * }</pre>
 */
@Slf4j
@Service
public class CircuitBreakerService {

    /** YAML 中定义的默认熔断配置名 */
    private static final String DEFAULT_CONFIG = "llm-default";

    private final CircuitBreakerRegistry cbRegistry;

    /** 按 providerName 缓存 Retry 实例，避免重复创建 */
    private final Map<String, Retry> retryCache = new ConcurrentHashMap<>();

    public CircuitBreakerService(CircuitBreakerRegistry cbRegistry) {
        this.cbRegistry = cbRegistry;
        log.info("CircuitBreakerService 初始化完成");
    }

    // ================================================================
    // 熔断器管理
    // ================================================================

    /**
     * 按 providerName 获取或创建独立熔断器实例.
     * <p>
     * 若 YAML 中已预定义该 provider 的实例配置则直接返回；
     * 否则以 {@code llm-default} 配置为模板动态创建。
     * </p>
     *
     * @param providerName provider 名称（openai / claude / gemini / ollama / 自定义）
     * @return 该 provider 专属的熔断器实例
     */
    public CircuitBreaker getOrCreate(String providerName) {
        return cbRegistry.find(providerName)
                .orElseGet(() -> {
                    log.info("动态创建熔断器 provider={} config={}", providerName, DEFAULT_CONFIG);
                    return cbRegistry.circuitBreaker(providerName, DEFAULT_CONFIG);
                });
    }

    // ================================================================
    // 韧性执行（熔断 + 重试）
    // ================================================================

    /**
     * 用熔断 + 重试装饰并<strong>立即执行</strong> Supplier.
     * <p>
     * 装饰顺序：CircuitBreaker(外层) → Retry(内层) → 实际调用。
     * </p>
     *
     * @param providerName provider 名称
     * @param supplier     实际 LLM 调用
     * @param <T>          返回值类型
     * @return 调用结果
     */
    public <T> T executeWithResilience(String providerName, Supplier<T> supplier) {
        return this.<T>decorate(providerName, supplier).get();
    }

    /**
     * 仅装饰 Supplier，返回装饰后的 {@link Supplier}，由调用方自行执行.
     * <p>
     * 装饰顺序：CircuitBreaker(外层) → Retry(内层) → 实际调用。
     * 先套 Retry，再套 CircuitBreaker，保证熔断打开时不会触发重试。
     * </p>
     *
     * @param providerName provider 名称
     * @param supplier     实际 LLM 调用
     * @param <T>          返回值类型
     * @return 装饰后的 Supplier
     */
    public <T> Supplier<T> decorate(String providerName, Supplier<T> supplier) {
        CircuitBreaker cb = getOrCreate(providerName);
        Retry retry = retryCache.computeIfAbsent(providerName, this::buildRetry);

        // Retry 内层（先装饰），CircuitBreaker 外层（后装饰）
        Supplier<T> withRetry = Retry.decorateSupplier(retry, supplier);
        return CircuitBreaker.decorateSupplier(cb, withRetry);
    }

    // ================================================================
    // 监控
    // ================================================================

    /**
     * 获取指定 provider 的熔断器当前状态.
     *
     * @param providerName provider 名称
     * @return CLOSED / OPEN / HALF_OPEN / DISABLED / FORCED_OPEN
     */
    public CircuitBreaker.State getState(String providerName) {
        return getOrCreate(providerName).getState();
    }

    /**
     * 获取指定 provider 的熔断器指标.
     * <p>
     * 包含 failureRate、numberOfFailedCalls、numberOfSuccessfulCalls 等。
     * </p>
     *
     * @param providerName provider 名称
     * @return 当前指标快照
     */
    public CircuitBreaker.Metrics getMetrics(String providerName) {
        return getOrCreate(providerName).getMetrics();
    }

    // ================================================================
    // 私有方法
    // ================================================================

    /**
     * 为指定 provider 构建 Retry 实例.
     * <p>
     * 使用 ThreadLocal 在 {@code retryOnException} 与 {@code intervalFunction} 之间
     * 传递异常类型，实现按异常类型返回不同的重试间隔：
     * <ul>
     *   <li>限流 (RATE_LIMITED)：退避 2s → 4s</li>
     *   <li>其他可重试异常：固定 1s</li>
     * </ul>
     * </p>
     */
    private Retry buildRetry(String providerName) {
        ThreadLocal<LlmApiException.Type> failureType = new ThreadLocal<>();

        RetryConfig config = RetryConfig.custom()
                .maxAttempts(3) // 1 次初始 + 2 次重试
                .retryOnException(e -> classifyAndShouldRetry(e, failureType))
                .intervalFunction(attempt -> computeInterval(providerName, attempt, failureType))
                .build();

        Retry retry = Retry.of(providerName + "-retry", config);

        // 事件监听：重试触发时记录 warn 日志
        retry.getEventPublisher()
                .onRetry(event -> log.warn("[{}] 重试触发 retryAttempt={} lastError={}",
                        providerName,
                        event.getNumberOfRetryAttempts(),
                        event.getLastThrowable() != null
                                ? event.getLastThrowable().getMessage()
                                : "unknown"));

        return retry;
    }

    /**
     * 判断异常是否应重试，并将异常类型写入 ThreadLocal 供 intervalFunction 使用.
     */
    private boolean classifyAndShouldRetry(Throwable e,
                                           ThreadLocal<LlmApiException.Type> failureType) {
        if (e instanceof LlmApiException lae) {
            failureType.set(lae.getType());
            return switch (lae.getType()) {
                case TIMEOUT, RATE_LIMITED, SERVER_ERROR, NETWORK_ERROR -> true;
                case AUTH_FAILED -> false;
            };
        }
        // 底层 IO 异常（尚未被包装为 LlmApiException）
        if (e instanceof SocketTimeoutException) {
            failureType.set(LlmApiException.Type.TIMEOUT);
            return true;
        }
        if (e instanceof ConnectException) {
            failureType.set(LlmApiException.Type.NETWORK_ERROR);
            return true;
        }
        if (e instanceof java.io.IOException) {
            failureType.set(LlmApiException.Type.NETWORK_ERROR);
            return true;
        }
        return false;
    }

    /**
     * 按异常类型计算重试间隔.
     * <p>
     * Resilience4j {@code IntervalFunction} 的 {@code attempt} 参数
     * 表示当前重试序号（从 1 开始）：
     * <ul>
     *   <li>attempt=1 → 第一次重试前的等待</li>
     *   <li>attempt=2 → 第二次重试前的等待</li>
     * </ul>
     * </p>
     */
    private long computeInterval(String providerName, int attempt,
                                 ThreadLocal<LlmApiException.Type> failureType) {
        LlmApiException.Type type = failureType.get();
        failureType.remove();

        if (type == LlmApiException.Type.RATE_LIMITED) {
            long delay = attempt == 1 ? 2000L : 4000L;
            log.info("[{}] 限流退避重试 attempt={} delay={}ms", providerName, attempt, delay);
            return delay;
        }

        log.info("[{}] 重试 attempt={} delay=1000ms type={}", providerName, attempt, type);
        return 1000L;
    }
}
