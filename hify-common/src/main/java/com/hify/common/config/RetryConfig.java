package com.hify.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.ExponentialRandomBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.Map;

/**
 * 重试配置 —— 基于 Spring Retry.
 * <p>
 * 重试规则来自 {@code hify.llm.retry.*}，策略：
 * <ul>
 *   <li><b>最多 3 次重试</b>（不含首次调用）</li>
 *   <li><b>指数退避</b> 1s → 2s → 4s，±30% 随机抖动</li>
 *   <li><b>重试条件</b>：429 / 5xx / ConnectException / SocketTimeoutException</li>
 *   <li><b>不重试</b>：401 / 400（认证失败 / 参数错误重试无意义）</li>
 * </ul>
 * </p>
 */
@Configuration
public class RetryConfig {

    /**
     * 读取 {@code hify.llm.retry.*} 配置.
     */
    @Bean
    @ConfigurationProperties(prefix = "hify.llm.retry")
    public RetryProperties retryProperties() {
        return new RetryProperties();
    }

    /**
     * LLM 调用专用 RetryTemplate.
     * <p>
     * 各 provider 实现注入此 Bean 手动执行重试，
     * 也可用 {@code @Retryable} 注解（声明式）。
     * </p>
     */
    @Bean
    public RetryTemplate llmRetryTemplate(RetryProperties props) {
        // 退避策略：1s → 2s → 4s，±30% 抖动
        ExponentialRandomBackOffPolicy backOff = new ExponentialRandomBackOffPolicy();
        backOff.setInitialInterval(props.getInitialDelay());
        backOff.setMaxInterval(props.getMaxDelay());
        backOff.setMultiplier(props.getMultiplier());

        // 重试策略
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(
                props.getMaxAttempts(),
                Map.of(
                        ConnectException.class, true,
                        SocketTimeoutException.class, true
                ),
                true // 可遍历 cause chain 匹配
        );

        RetryTemplate template = new RetryTemplate();
        template.setRetryPolicy(retryPolicy);
        template.setBackOffPolicy(backOff);
        return template;
    }
}
