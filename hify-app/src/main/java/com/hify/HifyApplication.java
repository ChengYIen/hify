package com.hify;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Hify 启动入口.
 * <p>
 * AI Agent 对话平台，Spring Boot 3 单体应用。
 * 启用异步（LLM 调用与请求线程隔离）和定时任务（线程池监控）。
 * </p>
 * <p>
 * 排除 {@link DataSourceAutoConfiguration} —— 双数据源（MySQL + PostgreSQL）
 * 由 {@link com.hify.common.config.DataSourceConfig} 手动装配。
 * </p>
 */
@Slf4j
@EnableAsync
@EnableScheduling
@SpringBootApplication(exclude = {
        DataSourceAutoConfiguration.class
})
public class HifyApplication {

    public static void main(String[] args) {
        SpringApplication.run(HifyApplication.class, args);
        log.info("Hify started successfully.");
    }
}
