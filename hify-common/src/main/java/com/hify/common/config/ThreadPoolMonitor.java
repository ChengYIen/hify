package com.hify.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 线程池监控 —— 定时打印 4 个线程池的核心指标.
 * <p>
 * 每 10 秒输出 active/pool/queue/completed，出问题时快速定位哪个池子满了。
 * 生产环境若日志量过大，可调整为 30s 或 60s。
 * </p>
 */
@Slf4j
@Component
public class ThreadPoolMonitor {

    private final Map<String, ThreadPoolTaskExecutor> executors;

    /**
     * 注入所有线程池 Bean，按名称索引.
     */
    public ThreadPoolMonitor(Map<String, ThreadPoolTaskExecutor> executors) {
        this.executors = executors;
    }

    /**
     * 每 10 秒输出一次线程池指标.
     */
    @Scheduled(fixedRate = 10_000)
    public void reportThreadPoolMetrics() {
        for (Map.Entry<String, ThreadPoolTaskExecutor> entry : executors.entrySet()) {
            String name = entry.getKey();
            ThreadPoolTaskExecutor executor = entry.getValue();
            var pool = executor.getThreadPoolExecutor();

            int active = pool.getActiveCount();
            int core = pool.getCorePoolSize();
            int max = pool.getMaximumPoolSize();
            int poolSize = pool.getPoolSize();
            int queue = pool.getQueue().size();
            int queueCap = pool.getQueue().remainingCapacity() + queue;
            long completed = pool.getCompletedTaskCount();

            // 只在有任务执行或有排队时打印 warn 日志
            if (active > 0 || queue > 0) {
                log.debug("线程池 [{}] active={}/{}/{} pool={} queue={}/{} completed={}",
                        name, active, core, max, poolSize, queue, queueCap, completed);
            }

            // 队列过半或线程池接近满载时告警
            if (poolSize >= max && queue > queueCap / 2) {
                log.warn("线程池 [{}] 压力过高! pool={}/{}, queue={}/{}",
                        name, poolSize, max, queue, queueCap);
            }
        }
    }
}
