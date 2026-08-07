package com.hify.common.config;

import com.hify.common.exception.ErrorCode;
import com.hify.common.exception.BizException;
import com.hify.common.web.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 简单滑动窗口限流切面.
 * <p>
 * 在 Controller 方法上加 {@code @RateLimit(key = "chat", maxRequests = 10, window = "60s")}
 * 即可按用户 IP 限流。限流计数器存储在 Redis，窗口过期自动清除。
 * </p>
 * <p>
 * 实现方式：Redis Lua 脚本原子性递增 + 设置过期，保证并发安全。
 * 仅在 {@link StringRedisTemplate} Bean 存在时生效（Redis 不可用时限流功能降级关闭）。
 * </p>
 */
@Slf4j
@Aspect
@Component("hifyRateLimiterAspect")
@ConditionalOnBean(StringRedisTemplate.class)
@RequiredArgsConstructor
public class RateLimiterAspect {

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * Lua 脚本：原子递增 → 首次设置 TTL → 返回当前计数.
     */
    private static final RedisScript<Long> INCR_SCRIPT = RedisScript.of(
            "local current = redis.call('INCR', KEYS[1]) "
                    + "if current == 1 then "
                    + "  redis.call('EXPIRE', KEYS[1], ARGV[1]) "
                    + "end "
                    + "return current",
            Long.class
    );

    /**
     * 切入 {@code @RateLimit} 注解的方法.
     * <p>
     * 返回 null 表示放行（不阻止正常流程），返回非 null Result 表示被限流。
     * </p>
     */
    @Around("@annotation(rateLimit)")
    public Object checkRateLimit(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        Long userId = UserContext.getUserId();
        String key = "hify:ratelimit:" + rateLimit.key() + ":"
                + (userId != null ? userId : "anonymous");

        Long current = stringRedisTemplate.execute(
                INCR_SCRIPT,
                List.of(key),
                String.valueOf(Duration.parse(rateLimit.window()).getSeconds())
        );

        if (current != null && current > rateLimit.maxRequests()) {
            log.warn("限流触发 key={}, current={}, max={}", key, current, rateLimit.maxRequests());
            throw new BizException(ErrorCode.RATE_LIMITED,
                    "请求过于频繁，请 " + rateLimit.window() + " 后重试");
        }

        return joinPoint.proceed();
    }
}
