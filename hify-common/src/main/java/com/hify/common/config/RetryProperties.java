package com.hify.common.config;

import lombok.Data;

/**
 * 重试配置属性 —— 绑定 {@code hify.llm.retry.*}.
 */
@Data
public class RetryProperties {

    /** 最多重试次数（不含首次调用） */
    private int maxAttempts = 3;

    /** 首次退避延迟（毫秒） */
    private long initialDelay = 1000;

    /** 最大退避延迟（毫秒） */
    private long maxDelay = 30_000;

    /** 退避乘数 2.0 = 每次翻倍 */
    private double multiplier = 2.0;

    /** 随机抖动比例 0.3 = ±30% */
    private double jitter = 0.3;
}
