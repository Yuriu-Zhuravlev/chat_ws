package com.yurii.zhuravlov.authservice.service;

import com.yurii.zhuravlov.authservice.dto.TokenPair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshGraceCacheTest {

    @Mock
    RedisTemplate<String, TokenPair> redis;
    @Mock
    ValueOperations<String, TokenPair> valueOps;

    @InjectMocks
    RefreshGraceCache cache;

    @Test
    void shouldReturnEmptyWhenRedisIsDown() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString()))
                .thenThrow(new RedisConnectionFailureException("down"));

        assertThatNoException()
                .isThrownBy(() -> assertThat(cache.find("hash")).isEmpty());
    }

    @Test
    void shouldNotPropagateFailureOnWrite() {
        when(redis.opsForValue()).thenReturn(valueOps);
        doThrow(new RedisConnectionFailureException("down"))
                .when(valueOps).set(anyString(), any(), any(Duration.class));

        assertThatNoException()
                .isThrownBy(() -> cache.put("hash", new TokenPair("a", "b")));
    }

    @Test
    void shouldStoreWithThirtySecondTtl() {
        when(redis.opsForValue()).thenReturn(valueOps);
        TokenPair pair = new TokenPair("a", "b");

        cache.put("hash", pair);

        verify(valueOps).set("refresh:used:hash", pair, Duration.ofSeconds(30));
    }
}