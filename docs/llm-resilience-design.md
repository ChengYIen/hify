# Hify LLM 调用韧性设计方案

> **场景：** Spring Boot 单体，同时对接 OpenAI / Claude / Gemini / Ollama，每个请求可能耗时 3 秒到 120 秒，网络波动和限流频繁。
> **目标：** 调得动、超时不死、失败了能恢复、别把服务拖垮。

---

## 一、线程管理

### 1.1 问题分析

LLM 调用是典型的 IO 密集型任务——线程大部分时间在等网络响应。如果复用 Tomcat 默认的 200 线程池，高峰期 50 个用户同时对话，每人一个 Agent 循环（调 3-5 次 LLM），瞬间占满并阻塞所有新请求。

**结论：LLM 调用必须与请求处理线程彻底分离。**

### 1.2 方案：双线程池隔离

```
请求线程池（Tomcat）                  业务线程池（LLM 专用）
┌─────────────────────┐              ┌─────────────────────────┐
│ http-nio-8080-exec-1 │ ──提交任务──▶ │ llm-task-1              │
│ http-nio-8080-exec-2 │              │ llm-task-2              │
│ http-nio-8080-exec-3 │              │ llm-task-3              │
│ ...        (max: 50) │              │ ...         (max: 100)  │
└─────────────────────┘              └─────────────────────────┘
                                             │
                                        永远不会阻塞
                                        Tomcat 线程
```

### 1.3 配置实现

```java
// common/config/LlmThreadPoolConfig.java
@Configuration
public class LlmThreadPoolConfig {

    /**
     * LLM 调用专用线程池
     * 核心线程少（节省资源），最大线程大（应对突发），队列不设上限（拒绝在主线程重试保底）
     */
    @Bean("llmExecutor")
    public ThreadPoolTaskExecutor llmExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);                          // 平时 8 个线程足够
        executor.setMaxPoolSize(100);                         // 高峰期最多 100
        executor.setQueueCapacity(0);                         // 不排队，直接创建新线程
        executor.setKeepAliveSeconds(120);                    // 空闲 2 分钟后回收
        executor.setThreadNamePrefix("llm-task-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // ↑ 线程池满时，由提交者线程（即 Tomcat 线程）自己执行——慢但不会丢任务
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /**
     * 轻量后台任务（日志记录、用量统计等）
     */
    @Bean("backgroundExecutor")
    public ThreadPoolTaskExecutor backgroundExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("bg-task-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardOldestPolicy());
        executor.initialize();
        return executor;
    }
}
```

### 1.4 异步编排——Agent 循环示例

Agent 循环的核心模式：调 LLM → 解析响应 → 如果需要调工具 → 执行工具 → 把结果喂回 LLM → 循环。每一步都是异步的。

```java
// module/conversation/service/impl/AgentLoopServiceImpl.java
@Service
@RequiredArgsConstructor
public class AgentLoopServiceImpl implements AgentLoopService {

    private final LlmProviderApi llmProviderApi;
    private final ToolExecutorApi toolExecutorApi;
    private final ThreadPoolTaskExecutor llmExecutor;

    @Override
    public CompletableFuture<AgentLoopResult> execute(AgentLoopRequest request) {
        return CompletableFuture
            .supplyAsync(() -> buildInitialMessages(request), llmExecutor)
            .thenCompose(messages -> callLlmWithLoop(messages, request.getTools(), 0));
    }

    /**
     * 递归 Agent 循环：LLM → 工具调用 → LLM → ... → 最终回复
     * 通过 @Async 注解简化版（需要启用 @EnableAsync）
     */
    @Async("llmExecutor")
    @Override
    public CompletableFuture<AgentLoopResult> executeAsync(AgentLoopRequest request) {
        List<Message> messages = buildInitialMessages(request);
        int iteration = 0;
        int maxIterations = request.getMaxIterations() != null ? request.getMaxIterations() : 10;

        while (iteration < maxIterations) {
            iteration++;
            LlmResponseDTO response = llmProviderApi.chat(new LlmRequestDTO(
                request.getProviderId(),
                request.getModel(),
                messages,
                request.getTools()
            ));

            if (response.getToolCalls() == null || response.getToolCalls().isEmpty()) {
                // LLM 给出最终回复
                return CompletableFuture.completedFuture(
                    new AgentLoopResult(response.getContent(), iteration, response.getUsage())
                );
            }

            // 执行工具调用并追加到消息列表
            for (ToolCallDTO toolCall : response.getToolCalls()) {
                ToolResult result = toolExecutorApi.execute(toolCall);
                messages.add(MessageDTO.toolResult(toolCall.getId(), result.getOutput()));
            }
            messages.add(MessageDTO.assistant(response.getContent(), response.getToolCalls()));
        }

        // 超过最大迭代次数，强制 LLM 给出总结
        messages.add(MessageDTO.user("请基于以上工具调用的结果给出总结回复"));
        LlmResponseDTO finalResponse = llmProviderApi.chat(new LlmRequestDTO(...));
        return CompletableFuture.completedFuture(
            new AgentLoopResult(finalResponse.getContent(), iteration, finalResponse.getUsage())
        );
    }
}
```

### 1.5 SSE 流式输出的线程模型

```
Tomcat 线程                     LLM 线程池                    HTTP 连接（长连接）
     │                              │                              │
     ├── submit async call ──────▶  │                              │
     │                              ├── open SSE stream ────────▶  │
     │   return SseEmitter ◀──────  │                              │
     │                              │   onChunk("你") ──▶ emit     │
     │                              │   onChunk("好") ──▶ emit     │
     │                              │   onChunk("！") ──▶ complete  │
```

```java
// module/conversation/controller/ConversationSseController.java
@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
public class ConversationSseController {

    private final AgentLoopService agentLoopService;

    @PostMapping(value = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable Long id, @Valid @RequestBody SendMessageRequest request) {
        SseEmitter emitter = new SseEmitter(120_000L); // 最长 2 分钟

        // 在 LLM 线程池中执行，不阻塞 Tomcat 线程
        CompletableFuture
            .supplyAsync(() -> agentLoopService.executeStream(request), llmExecutor)
            .thenAccept(streamResult -> {
                try {
                    for (StreamChunk chunk : streamResult.getChunks()) {
                        emitter.send(SseEmitter.event()
                            .name(chunk.getType())   // "text" / "tool_call" / "done" / "error"
                            .data(chunk.getData()));
                    }
                    emitter.complete();
                } catch (Exception e) {
                    emitter.completeWithError(e);
                }
            })
            .exceptionally(ex -> {
                try {
                    emitter.send(SseEmitter.event()
                        .name("error")
                        .data(Map.of("message", ex.getMessage())));
                } catch (IOException ignored) {}
                emitter.complete();
                return null;
            });

        return emitter;
    }
}
```

---

## 二、超时

### 2.1 超时分层

LLM 调用链路长，每层都要设超时，且超时时间要逐层递减：

```
用户请求（2 分钟）           ← 最外层，用户能等的上限
  └── Agent 循环（90 秒）     ← 多次 LLM 调用+工具调用的总时长
       ├── LLM 调用（60 秒）  ← 单次 LLM API 超时
       │    └── HTTP 连接（10 秒）← 建立连接超时
       │    └── HTTP 读取（60 秒）← 等待响应超时
       └── 工具调用（30 秒）  ← 本地/远程工具执行
```

### 2.2 配置实现

```yaml
# application.yml
hify:
  llm:
    timeout:
      connect: 10s          # 建立 TCP 连接的最长时间
      read: 60s             # 等待响应的最长时间
      write: 30s            # 发送请求体的最长时间
      total: 90s            # 单次调用的总超时（覆盖以上）
    agent:
      max-iterations: 10    # Agent 最大迭代次数
      total-timeout: 90s    # Agent 循环总超时
    tool:
      timeout: 30s          # 单次工具调用超时
```

### 2.3 RestClient 超时配置

```java
// common/config/LlmRestClientConfig.java
@Configuration
public class LlmRestClientConfig {

    @Bean("llmRestClient")
    public RestClient llmRestClient(LlmTimeoutProperties props) {
        return RestClient.builder()
            .requestFactory(new JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(props.getConnect().toMillis()))
                    .build()
            ))
            .defaultHeader("Content-Type", "application/json")
            .build();
    }

    /**
     * 每次调用时用此方法设置更细粒度的超时
     */
    public static RequestConfig requestConfig(LlmTimeoutProperties props) {
        return RequestConfig.custom()
            .setConnectionRequestTimeout((int) props.getConnect().toMillis())
            .setConnectTimeout((int) props.getConnect().toMillis())
            .setSocketTimeout((int) props.getRead().toMillis())
            .build();
    }
}
```

### 2.4 各模型的超时差异化

不同模型的响应速度差异很大，统一用 60 秒既不现实也浪费：

```java
// shared/llm/LlmTimeoutProperties.java
@ConfigurationProperties(prefix = "hify.llm")
@Data
public class LlmTimeoutProperties {
    private Duration connect = Duration.ofSeconds(10);
    private Duration read = Duration.ofSeconds(60);

    /**
     * 根据模型类型动态调整读取超时
     */
    public Duration getReadTimeoutForProvider(String providerType, String model) {
        return switch (providerType.toUpperCase()) {
            case "OPENAI", "ANTHROPIC" -> Duration.ofSeconds(60);    // 云端大模型
            case "GEMINI"                 -> Duration.ofSeconds(45);  // Google 稍快
            case "OLLAMA" -> {
                // 本地模型取决于硬件，用更长超时
                if (model != null && model.contains("70b")) {
                    yield Duration.ofSeconds(180);  // 大模型慢
                }
                yield Duration.ofSeconds(90);
            }
            default -> Duration.ofSeconds(60);
        };
    }
}
```

### 2.5 超时后的清理

```java
// 在 CompletableFuture 链末尾设置超时
CompletableFuture<LlmResponseDTO> future = CompletableFuture
    .supplyAsync(() -> llmProviderApi.chat(request), llmExecutor)
    .orTimeout(90, TimeUnit.SECONDS)              // JDK 9+ 的优雅超时
    .exceptionally(ex -> {
        if (ex instanceof TimeoutException) {
            throw new BusinessException(ErrorCode.LLM_TIMEOUT);
        }
        throw new BusinessException(ErrorCode.LLM_CALL_FAILED);
    });

// 或者用更可控的 future 编排
CompletableFuture<LlmResponseDTO> future = CompletableFuture
    .supplyAsync(() -> llmProviderApi.chat(request), llmExecutor);

try {
    return future.get(90, TimeUnit.SECONDS);      // 阻塞等待，超时抛异常
} catch (TimeoutException e) {
    future.cancel(true);                           // 中断底层 HTTP 请求
    throw new BusinessException(ErrorCode.LLM_TIMEOUT);
}
```

---

## 三、重试

### 3.1 什么该重试、什么不该

| 错误类型 | 示例 | 是否重试 |
|---|---|---|
| 网络瞬时故障 | Connection reset, timeout | ✅ 重试，指数退避 |
| 服务端 5xx | 502, 503, 504 | ✅ 重试，指数退避 |
| 限流 429 | rate_limit_exceeded | ✅ 重试，等 Retry-After 头 |
| 服务端 4xx（非限流） | 401（Key 无效）, 400（参数错误） | ❌ 不重试，直接抛错 |
| 业务超时 | 单次调用超过 60s 无响应 | ⚠️ 重试 1 次，换模型重试 |

### 3.2 配置

```yaml
hify:
  llm:
    retry:
      max-attempts: 3         # 最多 3 次（含首次）
      initial-delay: 1000ms   # 第一次重试等 1 秒
      max-delay: 30s          # 最大延迟上限
      multiplier: 2.0         # 指数退避因子：1s → 2s → 4s → ... → 上限 30s
      jitter: 0.3             # 随机抖动 ±30%，避免惊群效应
      retryable-statuses: [429, 502, 503, 504]
```

### 3.3 Spring Retry 实现

```java
// common/config/RetryConfig.java
@Configuration
@EnableRetry
public class RetryConfig {
    // 开启 @Retryable 注解支持
}

// module/provider/service/impl/LlmProviderApiImpl.java
@Service
@RequiredArgsConstructor
@Slf4j
public class LlmProviderApiImpl implements LlmProviderApi {

    @Override
    @Retryable(
        retryFor = {LlmRetryableException.class},
        maxAttemptsExpression = "#{${hify.llm.retry.max-attempts}}",
        backoff = @Backoff(
            delayExpression = "#{${hify.llm.retry.initial-delay}}",
            maxDelayExpression = "#{${hify.llm.retry.max-delay}}",
            multiplierExpression = "#{${hify.llm.retry.multiplier}}"
        ),
        listeners = {"llmRetryListener"}
    )
    public LlmResponseDTO chat(LlmRequestDTO request) {
        ProviderEntity provider = validateAndGetProvider(request.getProviderId());
        Duration readTimeout = timeoutProperties.getReadTimeoutForProvider(
            provider.getProviderType(), request.getModel()
        );
        return doChat(provider, request, readTimeout);
    }

    @Recover
    public LlmResponseDTO chatRecover(LlmRetryableException e, LlmRequestDTO request) {
        // 所有重试都失败后的兜底
        log.error("LLM call failed after {} retries, request: {}", 
                  retryProperties.getMaxAttempts(), request, e);
        throw new BusinessException(ErrorCode.LLM_ALL_RETRIES_EXHAUSTED);
    }

    private LlmResponseDTO doChat(ProviderEntity provider, LlmRequestDTO request, Duration timeout) {
        try {
            HttpResponse<String> response = sendHttpRequest(provider, request, timeout);

            return switch (response.statusCode()) {
                case 200 -> parseResponse(response.body());
                case 429 -> {
                    Duration wait = parseRetryAfter(response);
                    log.warn("Rate limited by {}, retry after {}", provider.getName(), wait);
                    throw new LlmRetryableException("Rate limited", wait);
                }
                case 502, 503, 504 -> {
                    log.warn("Provider {} returned {}, retrying", provider.getName(), response.statusCode());
                    throw new LlmRetryableException("Server error: " + response.statusCode());
                }
                case 401 -> throw new BusinessException(ErrorCode.LLM_INVALID_API_KEY);
                case 400 -> throw new BusinessException(ErrorCode.LLM_INVALID_REQUEST);
                default -> throw new LlmRetryableException("Unexpected status: " + response.statusCode());
            };
        } catch (java.net.SocketTimeoutException e) {
            // 区分连接超时 vs 读取超时
            log.warn("LLM call timeout: {}", e.getMessage());
            throw new LlmRetryableException("Timeout", null);
        } catch (java.net.ConnectException e) {
            log.warn("LLM provider unreachable: {}", provider.getName());
            throw new LlmRetryableException("Connection failed", null);
        }
    }
}
```

### 3.4 重试监听器——记录每次重试的元信息

```java
// common/config/LlmRetryListener.java
@Component
@Slf4j
public class LlmRetryListener implements RetryListener {

    @Override
    public <T, E extends Throwable> boolean open(RetryContext context, RetryCallback<T, E> callback) {
        context.setAttribute("startTime", System.currentTimeMillis());
        return true;
    }

    @Override
    public <T, E extends Throwable> void onError(
            RetryContext context, RetryCallback<T, E> callback, Throwable throwable) {

        int attempt = context.getRetryCount();
        long elapsed = System.currentTimeMillis() - (long) context.getAttribute("startTime");

        log.warn("LLM retry attempt #{}, elapsed {}ms, error: {}",
                 attempt, elapsed, throwable.getMessage());

        // 记录到监控指标
        MeterRegistry registry = SpringContextHolder.getBean(MeterRegistry.class);
        if (registry != null) {
            registry.counter("hify.llm.retry.total",
                "attempt", String.valueOf(attempt),
                "error", throwable.getClass().getSimpleName()
            ).increment();
        }
    }
}
```

### 3.5 带退避信息的异常

```java
// common/exception/LlmRetryableException.java
public class LlmRetryableException extends RuntimeException {
    private final Duration retryAfter; // 来自 429 Retry-After 头

    public LlmRetryableException(String message, Duration retryAfter) {
        super(message);
        this.retryAfter = retryAfter;
    }

    /**
     * 如果服务器返回了 Retry-After，优先用它；否则用退避公式
     */
    public Duration getSuggestedDelay(Duration backoffDelay) {
        return retryAfter != null
            ? retryAfter.plus(Duration.ofMillis(100)) // 多等 100ms 保安全
            : backoffDelay;
    }
}
```

### 3.6 模型降级重试

当主模型反复失败时，尝试切换到备选模型：

```java
// module/conversation/service/impl/ModelFallbackService.java
@Service
@Slf4j
public class ModelFallbackService {

    /**
     * 按优先级尝试多个模型
     */
    public LlmResponseDTO callWithFallback(LlmRequestDTO request, List<Long> fallbackProviderIds) {
        // 先试主模型（retry 已内置）
        try {
            return llmProviderApi.chat(request);
        } catch (BusinessException e) {
            if (e.getCode() == ErrorCode.LLM_ALL_RETRIES_EXHAUSTED.getCode()
                && fallbackProviderIds != null) {
                log.warn("Primary model exhausted, trying {} fallbacks", fallbackProviderIds.size());
            } else {
                throw e;
            }
        }

        // 逐一尝试备选模型
        for (Long fallbackId : fallbackProviderIds) {
            try {
                LlmRequestDTO fallbackRequest = request.withProviderId(fallbackId);
                return llmProviderApi.chat(fallbackRequest);
            } catch (BusinessException e) {
                log.warn("Fallback model {} also failed: {}", fallbackId, e.getMessage());
            }
        }

        throw new BusinessException(ErrorCode.LLM_ALL_MODELS_FAILED);
    }
}
```

---

## 四、容错（熔断与隔离）

### 4.1 为什么 LLM 调用特别需要熔断

普通 API 的熔断阈值可能是"1 分钟内错误率超过 50%"——但对 LLM 来说，限流 429 和超时非常频繁，如果一出现就熔断，用户感受极差。熔断器要语义感知：**5xx 和 Connection Timeout 才该熔断，429 和 Read Timeout 不该熔断。**

### 4.2 配置

```yaml
# application.yml
resilience4j:
  circuitbreaker:
    configs:
      llm-default:
        sliding-window-type: COUNT_BASED
        sliding-window-size: 20            # 最近 20 次调用
        failure-rate-threshold: 50         # 失败率超过 50% 才熔断
        wait-duration-in-open-state: 30s   # 熔断后 30 秒尝试半开
        permitted-number-of-calls-in-half-open-state: 2  # 半开时放 2 个请求探路
        automatic-transition-from-open-to-half-open-enabled: true
        # 关键：只有这些异常才计入熔断统计
        record-exceptions:
          - java.net.ConnectException
          - java.io.IOException
        # 429 和超时不触发熔断（它们会在重试层处理）
        ignore-exceptions:
          - com.hify.common.exception.LlmRateLimitException
    instances:
      openai:
        base-config: llm-default
      claude:
        base-config: llm-default
      gemini:
        base-config: llm-default
      ollama:
        base-config: llm-default
        failure-rate-threshold: 60         # 本地 Ollama 稳定性差，阈值放宽
        wait-duration-in-open-state: 15s   # 本地服务恢复快，半开更快

  timelimiter:
    configs:
      llm-default:
        timeout-duration: 90s              # 配合线程池的超时
        cancel-running-future: true        # 超时后真的中断底层 HTTP 请求
```

### 4.3 熔断 + 重试的编排顺序

```
调用链：
  @CircuitBreaker → @Retryable → 实际 HTTP 调用

为什么这个顺序：
  熔断在外层：如果已熔断，Retry 根本不会执行 → 省去无意义的重试等待
  重试在内层：每次重试都是独立的 HTTP 调用，不会被熔断器误判为"一次失败的请求"
```

```java
// shared/llm/LlmResilienceFacade.java
@Service
@RequiredArgsConstructor
@Slf4j
public class LlmResilienceFacade implements LlmProviderApi {

    private final LlmProviderApiImpl actualImpl;   // 实际的 HTTP 调用逻辑
    private final CircuitBreakerRegistry cbRegistry;
    private final MeterRegistry meterRegistry;

    @Override
    @CircuitBreaker(name = "llm", fallbackMethod = "circuitBrokenFallback")
    public LlmResponseDTO chat(LlmRequestDTO request) {
        // 根据 provider 动态选择熔断器实例
        return callWithProviderBreaker(request);
    }

    private LlmResponseDTO callWithProviderBreaker(LlmRequestDTO request) {
        ProviderEntity provider = getProvider(request.getProviderId());
        CircuitBreaker breaker = cbRegistry.circuitBreaker(
            provider.getProviderType().toLowerCase()  // "openai" / "claude" / "gemini" / "ollama"
        );

        return breaker.executeCallable(() -> {
            long start = System.currentTimeMillis();
            try {
                LlmResponseDTO result = actualImpl.chat(request);  // 内部有 @Retryable
                recordSuccess(provider, System.currentTimeMillis() - start);
                return result;
            } catch (Exception e) {
                recordFailure(provider, e);
                throw e;
            }
        });
    }

    /**
     * 熔断打开时的兜底逻辑
     */
    private LlmResponseDTO circuitBrokenFallback(LlmRequestDTO request, Exception e) {
        // 如果有其他可用的 provider，自动切换
        ProviderEntity fallback = findAvailableProvider(request.getProviderId());
        if (fallback != null) {
            log.warn("Circuit open for primary provider, falling back to {}", fallback.getName());
            return callWithProviderBreaker(request.withProviderId(fallback.getId()));
        }

        // 所有 provider 都不可用
        throw new BusinessException(ErrorCode.LLM_SERVICE_UNAVAILABLE,
            "所有 LLM 服务暂不可用，请稍后重试");
    }

    private ProviderEntity findAvailableProvider(Long excludeId) {
        // 找一个未熔断的同类型 provider
        List<ProviderEntity> all = providerMapper.selectAllActive();
        return all.stream()
            .filter(p -> !p.getId().equals(excludeId))
            .filter(p -> {
                CircuitBreaker cb = cbRegistry.circuitBreaker(p.getProviderType().toLowerCase());
                return cb.getState() != CircuitBreaker.State.OPEN;
            })
            .findFirst()
            .orElse(null);
    }

    private void recordSuccess(ProviderEntity provider, long latencyMs) {
        meterRegistry.timer("hify.llm.call",
            "provider", provider.getProviderType(),
            "status", "success"
        ).record(Duration.ofMillis(latencyMs));
    }

    private void recordFailure(ProviderEntity provider, Exception e) {
        meterRegistry.counter("hify.llm.call",
            "provider", provider.getProviderType(),
            "status", "failure",
            "error", e.getClass().getSimpleName()
        ).increment();
    }
}
```

### 4.4 各 provider 独立熔断

```
                         ┌──────────────────┐
                         │  CircuitBreaker   │
                         │  Registry         │
                         │                  │
   chat(openai) ────────▶│  "openai"  [CLOSED]  │──────▶ OpenAI API
   chat(claude) ────────▶│  "claude"  [CLOSED]  │──────▶ Claude API
   chat(gemini) ────────▶│  "gemini" [OPEN] ✗   │──╳───▶ Gemini API (挂掉，不调)
   chat(ollama) ────────▶│  "ollama"  [CLOSED]  │──────▶ Ollama (本地，稳)
                         └──────────────────┘

Gemini 挂了不影响 OpenAI/Claude/Ollama 的正常调用
```

---

## 五、完整调用链路全景

```
用户请求
  │
  ├── Controller 接收（Tomcat 线程，10ms）
  │     │
  │     └── 提交任务到 llmExecutor
  │
  ├── LLM 线程池（llm-task-N）
  │     │
  │     └── @Async 异步执行
  │           │
  │           ├── 1. 熔断检查（Resilience4j CircuitBreaker）
  │           │      └── OPEN → fallback 到备选 provider 或直接报错
  │           │
  │           ├── 2. 超时控制（CompletableFuture.orTimeout）
  │           │      └── Agent 总超时 90s / 单次 LLM 超时 60s
  │           │
  │           ├── 3. 实际 HTTP 调用（RestClient）
  │           │      ├── connect timeout: 10s
  │           │      ├── read timeout: 60s（或按模型动态调整）
  │           │      │
  │           │      ├── 成功 → 返回结果
  │           │      ├── 429 → LlmRetryableException（等 Retry-After）
  │           │      ├── 5xx → LlmRetryableException（指数退避）
  │           │      ├── timeout → LlmRetryableException（重试 1 次）
  │           │      └── 401/400 → BusinessException（不重试）
  │           │
  │           └── 4. 重试（Spring Retry @Retryable）
  │                  ├── 最多 3 次
  │                  ├── 1s → 2s → 4s（指数退避 + 抖动）
  │                  └── 全部失败 → 模型降级（切换 provider 再试）
  │
  └── 结果回传（SSE 流式 / CompletableFuture 回调 / 同步等待）
```

---

## 六、辅助代码

### 6.1 错误码补充

```java
// common/exception/ErrorCode.java 补充
LLM_TIMEOUT(60001, "LLM 调用超时，请重试"),
LLM_CALL_FAILED(60002, "LLM 调用失败"),
LLM_ALL_RETRIES_EXHAUSTED(60003, "LLM 重试耗尽，请稍后重试"),
LLM_INVALID_API_KEY(60004, "API Key 无效，请检查配置"),
LLM_INVALID_REQUEST(60005, "LLM 请求参数错误"),
LLM_SERVICE_UNAVAILABLE(60006, "LLM 服务暂不可用"),
LLM_ALL_MODELS_FAILED(60007, "所有可用模型均调用失败"),
```

### 6.2 启用异步支持

```java
// HifyApplication.java
@SpringBootApplication
@EnableAsync
@EnableRetry
@EnableScheduling
public class HifyApplication {
    public static void main(String[] args) {
        SpringApplication.run(HifyApplication.class, args);
    }
}
```

### 6.3 依赖补充（pom.xml）

```xml
<!-- 韧性四件套 -->
<dependency>
    <groupId>org.springframework.retry</groupId>
    <artifactId>spring-retry</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-aspects</artifactId>
</dependency>
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
</dependency>
<!-- 监控指标 -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

---

## 七、实施顺序建议

| 优先级 | 内容 | 理由 |
|---|---|---|
| 第 1 天 | 线程池隔离 + HTTP 超时配置 | 不隔离线程的话，一次大流量直接拖死整个服务 |
| 第 2 天 | 重试（区分 429/5xx/4xx）+ 指数退避 | 解决偶发失败，提升可用性到 95% |
| 第 3 天 | Resilience4j 熔断 + 按 provider 隔离 | 一个 provider 挂了不影响其他 |
| 第 4 天 | 模型降级 + 监控打点 | 生产可观测的最后拼图 |
