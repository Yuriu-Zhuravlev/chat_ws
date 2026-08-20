package com.yurii.zhuravlov.authservice.service;

import com.yurii.zhuravlov.authservice.IntegrationTestBase;
import com.yurii.zhuravlov.authservice.dto.TokenPair;
import com.yurii.zhuravlov.authservice.entities.RefreshToken;
import com.yurii.zhuravlov.authservice.entities.User;
import com.yurii.zhuravlov.authservice.exceptions.RefreshTokenException;
import com.yurii.zhuravlov.authservice.exceptions.TokenTheftException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionServiceIntegrationTest extends IntegrationTestBase {

    @Autowired
    SessionService sessionService;

    @Test
    void loginShouldRevokePreviousSession() {
        User user = givenUser("vasyl", "password123");

        TokenPair first = sessionService.startNewSession(user);
        TokenPair second = sessionService.startNewSession(user);

        List<RefreshToken> tokens = refreshTokenRepository.findAll();
        assertThat(tokens).hasSize(2);

        RefreshToken old = findByRaw(first.refreshToken());
        RefreshToken fresh = findByRaw(second.refreshToken());

        assertThat(old.getRevokedAt()).isNotNull();
        assertThat(fresh.getRevokedAt()).isNull();
        assertThat(fresh.getUsedAt()).isNull();
    }

    @Test
    void refreshShouldRotateTokenAndKeepOnlyOneActive() {
        User user = givenUser("vasyl", "password123");
        TokenPair issued = sessionService.startNewSession(user);

        TokenPair rotated = sessionService.refreshSession(
                TokenIssuer.sha256Hex(issued.refreshToken()));

        assertThat(rotated.refreshToken()).isNotEqualTo(issued.refreshToken());
        assertThat(findByRaw(issued.refreshToken()).getUsedAt()).isNotNull();
        assertThat(findByRaw(rotated.refreshToken()).getUsedAt()).isNull();
    }

    @Test
    void shouldRejectRevokedToken() {
        User user = givenUser("vasyl", "password123");
        TokenPair issued = sessionService.startNewSession(user);
        sessionService.sessionLogout(TokenIssuer.sha256Hex(issued.refreshToken()));

        assertThatThrownBy(() -> sessionService.refreshSession(
                TokenIssuer.sha256Hex(issued.refreshToken())))
                .isInstanceOf(RefreshTokenException.class);
    }

    @Test
    void shouldRejectUnknownToken() {
        assertThatThrownBy(() -> sessionService.refreshSession(
                TokenIssuer.sha256Hex("never-existed")))
                .isInstanceOf(RefreshTokenException.class);
    }

    @Test
    void reusedTokenOutsideGraceShouldTerminateSession() {
        User user = givenUser("vasyl", "password123");
        TokenPair first = sessionService.startNewSession(user);
        String firstHash = TokenIssuer.sha256Hex(first.refreshToken());

        TokenPair second = sessionService.refreshSession(firstHash);

        ageUsedAt(firstHash, Duration.ofMinutes(5));

        assertThatThrownBy(() -> sessionService.refreshSession(firstHash))
                .isInstanceOf(TokenTheftException.class);

        assertThat(findByRaw(second.refreshToken()).getRevokedAt()).isNotNull();

        assertThat(refreshTokenRepository.findAll())
                .noneMatch(t -> t.getUsedAt() == null && t.getRevokedAt() == null);

        assertThatThrownBy(() -> sessionService.refreshSession(
                TokenIssuer.sha256Hex(second.refreshToken())))
                .isInstanceOf(RefreshTokenException.class);
    }

    @Test
    void reusedTokenWithinGraceShouldNotTerminateSession() {
        User user = givenUser("vasyl", "password123");
        TokenPair first = sessionService.startNewSession(user);
        String firstHash = TokenIssuer.sha256Hex(first.refreshToken());

        TokenPair second = sessionService.refreshSession(firstHash);

        assertThatThrownBy(() -> sessionService.refreshSession(firstHash))
                .isInstanceOf(RefreshTokenException.class)
                .isNotInstanceOf(TokenTheftException.class);

        assertThat(findByRaw(second.refreshToken()).getRevokedAt()).isNull();
    }

    @Test
    void concurrentRefreshShouldNotTerminateSession() throws Exception {
        User user = givenUser("vasyl", "password123");
        TokenPair issued = sessionService.startNewSession(user);
        String hash = TokenIssuer.sha256Hex(issued.refreshToken());

        int threads = 5;
        try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
            CountDownLatch startGate = new CountDownLatch(1);

            List<Future<Object>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(executor.submit(() -> {
                    startGate.await();
                    try {
                        return sessionService.refreshSession(hash);
                    } catch (Exception e) {
                        return e;
                    }
                }));
            }

            startGate.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(15, TimeUnit.SECONDS)).isTrue();

            List<Object> results = new ArrayList<>();
            for (Future<Object> f : futures) {
                results.add(f.get());
            }

            long succeeded = results.stream().filter(r -> r instanceof TokenPair).count();
            assertThat(succeeded)
                    .isEqualTo(1);

            assertThat(results)
                    .noneMatch(r -> r instanceof TokenTheftException);

            assertThat(refreshTokenRepository.findAll())
                    .filteredOn(t -> t.getUsedAt() == null && t.getRevokedAt() == null)
                    .hasSize(1);
        }
    }

    @Test
    void concurrentLoginsShouldLeaveExactlyOneActiveSession() throws Exception {
        User user = givenUser("vasyl", "password123");

        int threads = 5;
        try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
            CountDownLatch gate = new CountDownLatch(1);
            AtomicInteger successes = new AtomicInteger();

            for (int i = 0; i < threads; i++) {
                executor.submit(() -> {
                    gate.await();
                    try {
                        sessionService.startNewSession(user);
                        successes.incrementAndGet();
                    } catch (Exception ignored) {
                    }
                    return null;
                });
            }

            gate.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(15, TimeUnit.SECONDS)).isTrue();

            assertThat(refreshTokenRepository.findAll())
                    .filteredOn(t -> t.getUsedAt() == null && t.getRevokedAt() == null)
                    .hasSize(1);

            assertThat(successes.get()).isGreaterThanOrEqualTo(1);
        }
    }

    private RefreshToken findByRaw(String rawToken) {
        return refreshTokenRepository.findByTokenHashWithUser(
                TokenIssuer.sha256Hex(rawToken)).orElseThrow();
    }
}