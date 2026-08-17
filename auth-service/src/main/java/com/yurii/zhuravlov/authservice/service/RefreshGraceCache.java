package com.yurii.zhuravlov.authservice.service;

import com.yurii.zhuravlov.authservice.dto.TokenPair;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshGraceCache {

    private static final String PREFIX = "refresh:used:";
    private static final Duration GRACE = Duration.ofSeconds(30);

    private final RedisTemplate<String, TokenPair> redis;

    public Optional<TokenPair> find(String tokenHash) {
        try {
            return Optional.ofNullable(redis.opsForValue().get(PREFIX + tokenHash));
        } catch (RuntimeException e) {
            log.warn("Redis unavailable on grace lookup", e);
            return Optional.empty();
        }
    }

    public void put(String tokenHash, TokenPair pair) {
        try {
            redis.opsForValue().set(PREFIX + tokenHash, pair, GRACE);
        } catch (RuntimeException e) {
            log.warn("Redis unavailable on grace store", e);
        }
    }
}