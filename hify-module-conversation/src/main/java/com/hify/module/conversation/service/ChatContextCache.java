package com.hify.module.conversation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 对话上下文 Redis 缓存（最近 N 轮热数据）.
 *
 * <p>结构：Redis List，每元素为 {@code {"role":"user","content":"..."}} 的 JSON 字符串，
 * RPUSH 追加即天然时间正序。Key 遵循 CLAUDE.md §14.1 命名：{@code hify:conversation:session:{id}}，
 * TTL 30 分钟（对话 session 状态）。写入时 LTRIM 到最近 {@code maxTurns*2} 条，
 * 保证 {@code LLEN == maxContextTurns*2}。</p>
 *
 * <p><b>降级铁律（CLAUDE.md 规则 43）：</b>Redis 不可用时所有操作跳过并打 debug 日志，
 * 由调用方回退 MySQL，绝不让对话流程报 500。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatContextCache {

    private static final String KEY_PREFIX = "hify:conversation:session:";
    private static final Duration TTL = Duration.ofMinutes(30);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /** 缓存中单条消息的载体（role + content），JSON 序列化存储。 */
    public record ContextMessage(String role, String content) {
    }

    /**
     * 追加一条消息到会话上下文，并裁剪到最近 {@code maxTurns*2} 条.
     *
     * @param maxTurns 上下文保留轮数（1 轮 = 1 问 + 1 答）
     */
    public void pushMessage(Long sessionId, String role, String content, int maxTurns) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(new ContextMessage(role, content));
        } catch (JsonProcessingException e) {
            log.warn("上下文消息序列化失败: sessionId={}, role={}", sessionId, role, e);
            return;
        }
        try {
            String key = key(sessionId);
            redisTemplate.opsForList().rightPush(key, payload);
            redisTemplate.expire(key, TTL);
            trimToTurns(key, maxTurns);
        } catch (Exception e) {
            log.debug("Redis 上下文缓存不可用，跳过写入: sessionId={}, err={}", sessionId, e.getMessage());
        }
    }

    /**
     * 读取会话最近上下文（时间正序）.
     *
     * @return 最近上下文；Redis 不可用或为空时返回空列表（由调用方回退 MySQL）
     */
    public List<ContextMessage> readRecent(Long sessionId) {
        try {
            List<String> raw = redisTemplate.opsForList().range(key(sessionId), 0, -1);
            if (raw == null || raw.isEmpty()) {
                return List.of();
            }
            List<ContextMessage> result = new ArrayList<>(raw.size());
            for (String json : raw) {
                result.add(objectMapper.readValue(json, ContextMessage.class));
            }
            return result;
        } catch (Exception e) {
            log.debug("Redis 上下文缓存不可用，回退 MySQL: sessionId={}, err={}", sessionId, e.getMessage());
            return List.of();
        }
    }

    /**
     * 用 MySQL 全量历史回填 Redis（Redis 为空 / TTL 过期后重建热缓存，避免与库内漂移）.
     */
    public void backfill(Long sessionId, List<ContextMessage> messages, int maxTurns) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        try {
            String key = key(sessionId);
            List<String> payloads = new ArrayList<>(messages.size());
            for (ContextMessage m : messages) {
                payloads.add(objectMapper.writeValueAsString(m));
            }
            redisTemplate.opsForList().rightPushAll(key, payloads);
            redisTemplate.expire(key, TTL);
            trimToTurns(key, maxTurns);
        } catch (Exception e) {
            log.debug("Redis 上下文缓存不可用，跳过回填: sessionId={}, err={}", sessionId, e.getMessage());
        }
    }

    /** 删除会话上下文缓存（会话归档/删除时调用）. */
    public void evict(Long sessionId) {
        try {
            redisTemplate.delete(key(sessionId));
        } catch (Exception e) {
            log.debug("Redis 上下文缓存不可用，跳过删除: sessionId={}, err={}", sessionId, e.getMessage());
        }
    }

    /** LTRIM 保留最近 {@code maxTurns*2} 条（最少 1 轮）。 */
    private void trimToTurns(String key, int maxTurns) {
        int cap = Math.max(1, maxTurns) * 2;
        Long size = redisTemplate.opsForList().size(key);
        if (size != null && size > cap) {
            redisTemplate.opsForList().trim(key, size - cap, -1);
        }
    }

    private String key(Long sessionId) {
        return KEY_PREFIX + sessionId;
    }
}
