package com.yurii.zhuravlov.notificationservice.ws;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class SessionRegistryTest {

    private final SessionRegistry registry = new SessionRegistry();

    @Test
    void returnsEmptySetForUnknownUser() {
        assertThat(registry.sessionsOf(42L)).isEmpty();
    }

    /** Several tabs of the same person are the normal case, not an edge case. */
    @Test
    void keepsEverySessionOfTheSameUser() {
        WebSocketSession first = mock(WebSocketSession.class);
        WebSocketSession second = mock(WebSocketSession.class);

        registry.register(1L, first);
        registry.register(1L, second);

        assertThat(registry.sessionsOf(1L)).containsExactlyInAnyOrder(first, second);
        assertThat(registry.connectedUsers()).isEqualTo(1);
    }

    @Test
    void keepsUsersApart() {
        WebSocketSession mine = mock(WebSocketSession.class);
        WebSocketSession theirs = mock(WebSocketSession.class);

        registry.register(1L, mine);
        registry.register(2L, theirs);

        assertThat(registry.sessionsOf(1L)).containsExactly(mine);
        assertThat(registry.sessionsOf(2L)).containsExactly(theirs);
    }

    /** The entry must go when the last session does, or the map grows forever. */
    @Test
    void dropsTheEntryWithTheLastSession() {
        WebSocketSession first = mock(WebSocketSession.class);
        WebSocketSession second = mock(WebSocketSession.class);

        registry.register(1L, first);
        registry.register(1L, second);

        registry.unregister(1L, first);
        assertThat(registry.connectedUsers()).isEqualTo(1);

        registry.unregister(1L, second);
        assertThat(registry.connectedUsers()).isZero();
        assertThat(registry.sessionsOf(1L)).isEmpty();
    }

    @Test
    void unregisteringUnknownSessionIsHarmless() {
        registry.unregister(1L, mock(WebSocketSession.class));

        assertThat(registry.connectedUsers()).isZero();
    }

    /** Callers get a view, not the live set: mutating it would bypass the entry bookkeeping. */
    @Test
    void exposesSessionsAsUnmodifiable() {
        registry.register(1L, mock(WebSocketSession.class));

        assertThatThrownBy(() -> registry.sessionsOf(1L).add(mock(WebSocketSession.class)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /**
     * Reproduces the reconnect window: one thread registers a new session while another
     * unregisters the previous one. With a non-atomic register the new session lands in
     * a set that the removal has already detached from the map, and the user goes silent.
     * Repeated because the window is narrow.
     */
    @RepeatedTest(20)
    void doesNotLoseSessionsWhenRegisterRacesWithUnregister() throws Exception {
        WebSocketSession leaving = mock(WebSocketSession.class);
        WebSocketSession arriving = mock(WebSocketSession.class);
        registry.register(1L, leaving);

        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            executor.submit(() -> {
                await(start);
                registry.unregister(1L, leaving);
            });
            executor.submit(() -> {
                await(start);
                registry.register(1L, arriving);
            });

            start.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(registry.sessionsOf(1L)).containsExactly(arriving);
    }

    @Test
    void survivesConcurrentChurn() throws Exception {
        int threads = 16;
        List<WebSocketSession> sessions = IntStream.range(0, threads)
                .mapToObj(i -> mock(WebSocketSession.class))
                .toList();

        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
            sessions.forEach(session -> executor.submit(() -> {
                await(start);
                registry.register(1L, session);
                registry.unregister(1L, session);
            }));

            start.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(registry.connectedUsers()).isZero();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}