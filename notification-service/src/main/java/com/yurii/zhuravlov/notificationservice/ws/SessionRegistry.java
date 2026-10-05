package com.yurii.zhuravlov.notificationservice.ws;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SessionRegistry {

    private final Map<Long, Set<WebSocketSession>> sessionsByUser = new ConcurrentHashMap<>();

    public void register(Long userId, WebSocketSession session) {
        sessionsByUser.compute(userId, (id, sessions) -> {
            Set<WebSocketSession> target = (sessions != null) ? sessions : ConcurrentHashMap.newKeySet();
            target.add(session);
            return target;
        });
    }

    /**
     * Returning null from computeIfPresent removes the entry, so a user who disconnects
     * leaves nothing behind. Without it the map would keep an empty set per user that
     * ever connected — a slow leak in a process that is meant to run for weeks.
     */
    public void unregister(Long userId, WebSocketSession session) {
        sessionsByUser.computeIfPresent(userId, (id, sessions) -> {
            sessions.remove(session);
            return sessions.isEmpty() ? null : sessions;
        });
    }

    public Set<WebSocketSession> sessionsOf(Long userId) {
        Set<WebSocketSession> sessions = sessionsByUser.get(userId);
        return sessions == null ? Set.of() : Collections.unmodifiableSet(sessions);
    }

    public int connectedUsers() {
        return sessionsByUser.size();
    }

    public Collection<WebSocketSession> allSessions() {
        return sessionsByUser.values().stream().flatMap(Set::stream).toList();
    }
}