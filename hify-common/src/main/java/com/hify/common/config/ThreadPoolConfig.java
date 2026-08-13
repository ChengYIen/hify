package com.hify.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 线程池配置.
 * <p>
 * 手动创建线程池，禁止使用 {@code Executors.newXxx()}。
 * </p>
 *
 * <h3>线程池分工</h3>
 * <table>
 *   <tr><th>线程池</th><th>核心/最大</th><th>队列</th><th>拒绝策略</th><th>职责</th></tr>
 *   <tr><td>llmExecutor</td><td>10/50</td><td>100</td><td>CallerRuns</td><td>LLM API 调用（IO 密集型）</td></tr>
 *   <tr><td>asyncExecutor</td><td>2/4</td><td>100</td><td>CallerRuns</td><td>文档处理、日志异步写入等后台任务</td></tr>
 *   <tr><td>heartbeatScheduler</td><td>2（调度器）</td><td>-</td><td>-</td><td>SSE 心跳保活（周期任务，daemon）</td></tr>
 * </table>
 */
@Slf4j
@Configuration
public class ThreadPoolConfig {

    /** SSE 心跳线程命名计数器 */
    private static final AtomicInteger HEARTBEAT_THREAD_COUNTER = new AtomicInteger(1);

    // ================================================================
    // llmExecutor — LLM API 调用
    // ================================================================

    /**
     * LLM 调用专用线程池.
     * <p>
     * LLM 调用必须与 Tomcat 请求线程隔离——Controller 提交任务到此线程池后立即释放。
     * 拒绝策略 {@link ThreadPoolExecutor.CallerRunsPolicy}：队列满时由调用方线程执行，
     * 起到背压作用，避免任务无限堆积。
     * </p>
     */
    @Bean("llmExecutor")
    public ThreadPoolTaskExecutor llmExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("llm-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        log.info("llmExecutor 初始化完成: core=10, max=50, queue=100, reject=CallerRuns");
        return executor;
    }

    // ================================================================
    // asyncExecutor — 文档处理、日志异步写入等后台任务
    // ================================================================

    /**
     * 异步任务线程池.
     * <p>
     * 用于文档解析/向量化、日志写入、用量统计等后台任务。队列容量 100，
     * 允许一定程度的任务堆积。拒绝策略同样使用 CallerRunsPolicy 保底。
     * </p>
     */
    @Bean("asyncExecutor")
    public ThreadPoolTaskExecutor asyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        log.info("asyncExecutor 初始化完成: core=2, max=4, queue=100, reject=CallerRuns");
        return executor;
    }

    // ================================================================
    // heartbeatScheduler — SSE 心跳保活
    // ================================================================

    /**
     * SSE 心跳定时器.
     * <p>
     * 流式对话的长连接可能被网关（Nginx/云 LB）因"长时间无数据"掐断，
     * 此调度器每 10s 向 {@code SseEmitter} 发送一个 SSE comment（{@code :ping}）保持连接活性。
     * 任务量极小（每条流 1 个周期任务），2 个 daemon 线程足够。
     * </p>
     * <p>
     * 手写 {@link ThreadFactory} 命名线程 + daemon 标记，避免非守护线程阻止应用关闭。
     * </p>
     */
    @Bean("heartbeatScheduler")
    public ScheduledThreadPoolExecutor heartbeatScheduler() {
        ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(2, r -> {
            Thread t = new Thread(r, "heartbeat-" + HEARTBEAT_THREAD_COUNTER.getAndIncrement());
            t.setDaemon(true);
            return t;
        });
        // 取消任务后立刻释放其持有的资源，避免已取消的周期任务残留
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        scheduler.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        log.info("heartbeatScheduler 初始化完成: core=2, daemon=true");
        return scheduler;
    }
}
