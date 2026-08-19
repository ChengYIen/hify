package com.hify.common.health;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis 健康检查 —— 执行 {@code PING} 命令.
 * <p>
 * 与 {@link MySqlHealthIndicator} 同理，供 K8s readiness probe 使用。
 * 注意：不抛出异常，Redis 不可用时返回 DOWN 而非 500。
 * </p>
 */
@Component
@ConditionalOnBean(RedisTemplate.class)
@RequiredArgsConstructor
public class RedisHealthIndicator implements HealthIndicator {

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public Health health() {
        try (RedisConnection conn =
                     redisTemplate.getRequiredConnectionFactory().getConnection()) {
            String pong = conn.ping();
            return Health.up()
                    .withDetail("redis", pong)
                    .build();
        } catch (Exception e) {
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
