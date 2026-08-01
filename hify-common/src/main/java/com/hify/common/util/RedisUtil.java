package com.hify.common.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

/**
 * Redis 基础工具类.
 * <p>
 * 封装常用的 Redis 操作：get / set / delete / expire / hasKey。
 * 方法层面做了空值防护和异常日志，不吞异常，由调用方决定降级策略。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisUtil {

    private final RedisTemplate<String, Object> redisTemplate;

    // ----------------------------------------------------
    // 写入
    // ----------------------------------------------------

    /**
     * 写入缓存（无 TTL，慎用）.
     * <p>
     * 按项目规范，生产 key 应设置过期时间，优先使用 {@link #set(String, Object, long, TimeUnit)}。
     * </p>
     *
     * @param key   键
     * @param value 值
     */
    public void set(String key, Object value) {
        try {
            redisTemplate.opsForValue().set(key, value);
        } catch (Exception e) {
            log.error("Redis set error, key={}", key, e);
            throw e;
        }
    }

    /**
     * 写入缓存并设置过期时间.
     *
     * @param key     键
     * @param value   值
     * @param timeout 过期时长
     * @param unit    时间单位
     */
    public void set(String key, Object value, long timeout, TimeUnit unit) {
        try {
            redisTemplate.opsForValue().set(key, value, timeout, unit);
        } catch (Exception e) {
            log.error("Redis set error, key={}, timeout={}{}", key, timeout, unit, e);
            throw e;
        }
    }

    // ----------------------------------------------------
    // 读取
    // ----------------------------------------------------

    /**
     * 读取缓存.
     *
     * @param key 键
     * @return 值，key 不存在返回 null
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        try {
            return (T) redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.error("Redis get error, key={}", key, e);
            throw e;
        }
    }

    // ----------------------------------------------------
    // 删除
    // ----------------------------------------------------

    /**
     * 删除单个 key.
     *
     * @param key 键
     * @return true 删除成功，false key 不存在
     */
    public boolean delete(String key) {
        try {
            Boolean result = redisTemplate.delete(key);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.error("Redis delete error, key={}", key, e);
            throw e;
        }
    }

    /**
     * 批量删除 key.
     *
     * @param keys 键集合
     * @return 实际删除的 key 数量
     */
    public long delete(Collection<String> keys) {
        try {
            Long count = redisTemplate.delete(keys);
            return count != null ? count : 0L;
        } catch (Exception e) {
            log.error("Redis delete error, keys={}", keys, e);
            throw e;
        }
    }

    // ----------------------------------------------------
    // 过期
    // ----------------------------------------------------

    /**
     * 设置 key 过期时间.
     *
     * @param key     键
     * @param timeout 过期时长
     * @param unit    时间单位
     * @return true 成功，false key 不存在或设置失败
     */
    public boolean expire(String key, long timeout, TimeUnit unit) {
        try {
            Boolean result = redisTemplate.expire(key, timeout, unit);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.error("Redis expire error, key={}, timeout={}{}", key, timeout, unit, e);
            throw e;
        }
    }

    /**
     * 获取 key 剩余过期时间.
     *
     * @param key  键
     * @param unit 时间单位
     * @return 剩余过期时间，-1 = 永不过期，-2 = key 不存在
     */
    public long getExpire(String key, TimeUnit unit) {
        try {
            Long ttl = redisTemplate.getExpire(key, unit);
            return ttl != null ? ttl : -2L;
        } catch (Exception e) {
            log.error("Redis getExpire error, key={}", key, e);
            throw e;
        }
    }

    // ----------------------------------------------------
    // 判断
    // ----------------------------------------------------

    /**
     * 判断 key 是否存在.
     *
     * @param key 键
     * @return true 存在
     */
    public boolean hasKey(String key) {
        try {
            Boolean result = redisTemplate.hasKey(key);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.error("Redis hasKey error, key={}", key, e);
            throw e;
        }
    }
}
