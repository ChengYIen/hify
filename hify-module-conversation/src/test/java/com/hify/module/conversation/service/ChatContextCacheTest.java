package com.hify.module.conversation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.module.conversation.service.ChatContextCache.ContextMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ChatContextCache 单元测试：push 裁剪、读映射、Redis 不可用降级.
 */
@ExtendWith(MockitoExtension.class)
class ChatContextCacheTest {

    private static final String KEY = "hify:conversation:session:7";

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ListOperations<String, String> listOps;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ChatContextCache cache;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForList()).thenReturn(listOps);
        cache = new ChatContextCache(redisTemplate, objectMapper);
    }

    @Test
    void should_push_and_trim_to_max_turns() {
        when(listOps.size(KEY)).thenReturn(8L);

        cache.pushMessage(7L, "user", "你好", 3);

        verify(listOps).rightPush(KEY, "{\"role\":\"user\",\"content\":\"你好\"}");
        verify(redisTemplate).expire(KEY, Duration.ofMinutes(30));
        // 8 条 > 3*2=6 → LTRIM 保留最近 6 条（start=8-6=2, end=-1）
        verify(listOps).trim(KEY, 2L, -1L);
    }

    @Test
    void should_not_trim_when_within_cap() {
        when(listOps.size(KEY)).thenReturn(6L);

        cache.pushMessage(7L, "user", "hi", 3);

        verify(listOps, never()).trim(anyString(), anyLong(), anyLong());
    }

    @Test
    void should_read_recent_in_chronological_order() {
        when(listOps.range(KEY, 0L, -1L))
                .thenReturn(List.of(
                        "{\"role\":\"user\",\"content\":\"你好\"}",
                        "{\"role\":\"assistant\",\"content\":\"hello\"}"));

        List<ContextMessage> result = cache.readRecent(7L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).role()).isEqualTo("user");
        assertThat(result.get(0).content()).isEqualTo("你好");
        assertThat(result.get(1).role()).isEqualTo("assistant");
    }

    @Test
    void should_return_empty_when_redis_down_on_read() {
        when(listOps.range(KEY, 0L, -1L)).thenThrow(new RedisConnectionFailureException("down"));

        assertThat(cache.readRecent(7L)).isEmpty();
    }

    @Test
    void should_skip_write_when_redis_down() {
        when(listOps.rightPush(anyString(), anyString())).thenThrow(new RedisConnectionFailureException("down"));

        // 降级铁律：Redis 挂了写缓存必须静默跳过，不抛异常
        assertThatCode(() -> cache.pushMessage(7L, "user", "hi", 3)).doesNotThrowAnyException();
    }

    @Test
    void should_backfill_when_redis_empty() {
        cache.backfill(7L,
                List.of(new ContextMessage("user", "a"), new ContextMessage("assistant", "b")), 3);

        verify(listOps).rightPushAll(eq(KEY), anyCollection());
        verify(redisTemplate).expire(KEY, Duration.ofMinutes(30));
    }
}
