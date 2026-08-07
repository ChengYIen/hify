package com.hify.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

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
 *   <tr><td>asyncExecutor</td><td>5/20</td><td>200</td><td>CallerRuns</td><td>日志异步写入等非关键任务</td></tr>
 * </table>
 */
@Slf4j
@Configuration
public class ThreadPoolConfig {

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
    // asyncExecutor — 日志异步写入等非关键任务
    // ================================================================

    /**
     * 异步任务线程池.
     * <p>
     * 用于日志写入、用量统计等非关键后台任务。队列容量较大（200），
     * 允许一定程度的任务堆积。拒绝策略同样使用 CallerRunsPolicy 保底。
     * </p>
     */
    @Bean("asyncExecutor")
    public ThreadPoolTaskExecutor asyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        log.info("asyncExecutor 初始化完成: core=5, max=20, queue=200, reject=CallerRuns");
        return executor;
    }
}
