package com.yurii.zhuravlov.authservice;

import com.yurii.zhuravlov.authservice.entities.User;
import com.yurii.zhuravlov.authservice.repo.RefreshTokenRepository;
import com.yurii.zhuravlov.authservice.repo.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;

@SpringBootTest
@Import({TestcontainersConfiguration.class, TestKeyConfiguration.class})
@ActiveProfiles("test")
@AutoConfigureMockMvc
public abstract class IntegrationTestBase {

    @Autowired
    protected RefreshTokenRepository refreshTokenRepository;
    @Autowired
    protected UserRepository userRepository;
    @Autowired
    private RedisConnectionFactory redisConnectionFactory;
    @Autowired
    protected PasswordEncoder passwordEncoder;
    @Autowired
    protected JdbcTemplate jdbcTemplate;
    @Autowired
    protected StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    void cleanState() {
        refreshTokenRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            connection.serverCommands().flushDb();
        }
    }

    protected User givenUser(String username, String rawPassword) {
        return userRepository.saveAndFlush(
                new User(passwordEncoder.encode(rawPassword), username));
    }

    protected void ageUsedAt(String tokenHash, Duration age) {
        jdbcTemplate.update(
                "UPDATE auth_schema.refresh_tokens SET used_at = used_at - ?::interval " +
                        "WHERE token_hash = ?",
                age.toSeconds() + " seconds", tokenHash);
    }

    protected void evictGrace(String tokenHash) {
        stringRedisTemplate.delete("refresh:used:" + tokenHash);
    }
}