package com.hify.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 配置.
 * <p>
 * 定义 RedisTemplate 的序列化方式：
 * key 使用 String 序列化，value 使用 JSON 序列化（Jackson），
 * 避免默认 JDK 序列化导致的可读性问题。
 * </p>
 * <p>
 * 作为普通用户配置类，注册顺序先于自动配置的 {@code RedisAutoConfiguration} ——
 * 自动配置的 redisTemplate Bean 带 {@code @ConditionalOnMissingBean(name = "redisTemplate")}，
 * 检测到同名 Bean 后自动让位，因此此处定义的 Bean 不会与自动配置冲突。
 * </p>
 * <p>
 * 注意：不能在类级使用 {@code @ConditionalOnBean(RedisConnectionFactory.class)} ——
 * 用户配置类先于自动配置处理，评估条件时 RedisConnectionFactory 尚未注册，条件永不满足，
 * 会导致 RedisTemplate/RedisUtil 永不创建（实测 RedisUtil bean 数为 0，Redis 缓存整体失效）。
 * 连接工厂由 spring-boot-starter-data-redis 自动装配；Redis 不可用属于运行时连接失败，
 * 由各调用方按降级策略处理（CLAUDE.md §43），不阻塞启动。
 * </p>
 */
@Configuration
public class RedisConfig {

    /**
     * RedisTemplate Bean —— key String 序列化，value JSON 序列化.
     * <p>
     * 使用 {@link GenericJackson2JsonRedisSerializer} 替代
     * {@code Jackson2JsonRedisSerializer(Object.class)}，
     * 后者在反序列化泛型容器（List/Map）时会丢失类型信息。
     * </p>
     *
     * @param factory Redis 连接工厂
     * @return 配置好的 RedisTemplate
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        // key / hashKey 统一用 String 序列化，可读性好，杜绝乱码
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // value / hashValue 用 Jackson JSON 序列化，存入时携带 @class 类型信息
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer();
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }
}
