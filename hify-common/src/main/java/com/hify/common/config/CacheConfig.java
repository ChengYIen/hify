package com.hify.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Spring Cache 配置 —— 注解声明式缓存.
 * <p>
 * 启用 {@link EnableCaching} 后，Service 方法加
 * {@code @Cacheable} / {@code @CacheEvict} 即可自动走 Redis。
 * </p>
 *
 * <h3>约定</h3>
 * <ul>
 *   <li>写操作 <b>必须加 {@code @CacheEvict}</b>，删缓存不更新</li>
 *   <li>DAO 内部不注解，注解只加在 Service 层 public 方法上</li>
 *   <li>Redis 不可用时自动降级，不会因为缓存不可用而报 500</li>
 * </ul>
 *
 * <h3>TTL</h3>
 * <table>
 *   <tr><th>缓存</th><th>TTL</th></tr>
 *   <tr><td>provider-cache</td><td>30 min</td></tr>
 *   <tr><td>agent-cache</td><td>30 min</td></tr>
 *   <tr><td>session-cache</td><td>2 hours</td></tr>
 *   <tr><td>其他（默认）</td><td>30 min</td></tr>
 * </table>
 */
@Configuration
@EnableCaching
@ConditionalOnBean(RedisConnectionFactory.class)
public class CacheConfig {

    /**
     * RedisCacheManager —— key String 序列化，value JSON 序列化.
     * <p>
     * key 格式：{@code hify:<cacheName>::<key>}。
     * 不同缓存名可配置不同 TTL（见 {@link CacheNames}）。
     * </p>
     */
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        // 默认 TTL 30 分钟
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30))
                .prefixCacheNameWith("hify:")
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(new GenericJackson2JsonRedisSerializer()));

        // 按缓存名定制 TTL
        Map<String, RedisCacheConfiguration> ttlMap = new HashMap<>();
        ttlMap.put(CacheNames.PROVIDER, ttlConfig(Duration.ofMinutes(30)));
        ttlMap.put(CacheNames.AGENT, ttlConfig(Duration.ofMinutes(30)));
        ttlMap.put(CacheNames.SESSION, ttlConfig(Duration.ofHours(2)));

        return RedisCacheManager.builder(factory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(ttlMap)
                .build();
    }

    private static RedisCacheConfiguration ttlConfig(Duration ttl) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .prefixCacheNameWith("hify:")
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(new GenericJackson2JsonRedisSerializer()));
    }
}
