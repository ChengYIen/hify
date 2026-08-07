package com.hify.common.config;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口限流注解 —— 标注在 Controller 方法上.
 * <p>
 * 限流计数器按用户 ID（未登录则按 IP）存储在 Redis。
 * </p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 *   @RateLimit(key = "chat", maxRequests = 10, window = "60s")
 *   @PostMapping("/chat")
 *   public Result<String> chat(@Valid @RequestBody ChatRequest request) { ... }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    /** 限流标识（如 chat / upload / login） */
    String key();

    /** 窗口内最大请求数 */
    int maxRequests() default 10;

    /** 时间窗口（Duration 格式：60s / 1m / 5m） */
    String window() default "60s";
}
