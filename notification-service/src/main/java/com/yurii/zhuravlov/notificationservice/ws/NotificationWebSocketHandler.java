package com.yurii.zhuravlov.notificationservice.ws;

import com.yurii.zhuravlov.notificationservice.config.properties.WebSocketProperties;
import com.yurii.zhuravlov.notificationservice.security.WebSocketAuthenticator;
import com.yurii.zhuravlov.notificationservice.ws.frame.ClientFrame;
import com.yurii.zhuravlov.notificationservice.ws.frame.ServerFrame;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationWebSocketHandler extends TextWebSocketHandler {

    /** 4000-4999 is the range reserved for application-defined close codes. */
    private static final CloseStatus UNAUTHORIZED = new CloseStatus(4401, "Unauthorized");
    private static final CloseStatus TOKEN_EXPIRED = new CloseStatus(4402, "Token expired");

    private static final String ATTR_USER_ID = "userId";
    private static final String ATTR_SESSION = "concurrentSession";
    private static final String ATTR_AUTH_TIMEOUT = "authTimeoutTask";
    private static final String ATTR_EXPIRY = "expiryTask";

    private static final int SEND_TIME_LIMIT_MS = 5_000;
    private static final int SEND_BUFFER_LIMIT_BYTES = 512 * 1024;

    private final WebSocketAuthenticator authenticator;
    private final SessionRegistry registry;
    private final ObjectMapper objectMapper;
    private final WebSocketProperties properties;
    private final TaskScheduler taskScheduler;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        ScheduledFuture<?> timeout = taskScheduler.schedule(
                () -> close(session, UNAUTHORIZED),
                Instant.now().plus(properties.authTimeout()));

        // Kept on the session rather than in a map: it is cleaned up with the session,
        // so a connection that dies abruptly cannot leak a task.
        session.getAttributes().put(ATTR_AUTH_TIMEOUT, timeout);
    }

    @Override
    protected void handleTextMessage(@NonNull WebSocketSession session, @NonNull TextMessage message) {
        ClientFrame frame;
        try {
            frame = objectMapper.readValue(message.getPayload(), ClientFrame.class);
        } catch (Exception e) {
            send(session, ServerFrame.error("Malformed frame"));
            return;
        }

        if (frame.type() == null) {
            send(session, ServerFrame.error("Missing frame type"));
            return;
        }

        boolean authenticated = session.getAttributes().containsKey(ATTR_USER_ID);

        if (ClientFrame.AUTH.equals(frame.type())) {
            if (authenticated) {
                send(session, ServerFrame.error("Already authenticated"));
                return;
            }
            authenticate(session, frame.token());
            return;
        }

        // Anything other than AUTH before authentication is a protocol violation, and
        // answering it would leak that the endpoint exists to an unauthenticated peer.
        if (!authenticated) {
            close(session, UNAUTHORIZED);
            return;
        }

        if (ClientFrame.PING.equals(frame.type())) {
            send(session, ServerFrame.pong());
            return;
        }

        send(session, ServerFrame.error("Unknown frame type: " + frame.type()));
    }

    private void authenticate(WebSocketSession session, String token) {
        if (token == null || token.isBlank()) {
            close(session, UNAUTHORIZED);
            return;
        }

        WebSocketAuthenticator.AuthenticatedUser user;
        try {
            user = authenticator.authenticate(token);
        } catch (Exception e) {
            log.debug("WebSocket authentication failed: {}", e.getMessage());
            close(session, UNAUTHORIZED);
            return;
        }

        cancel(session, ATTR_AUTH_TIMEOUT);

        // sendMessage is not thread-safe, and from now on the Kafka consumer thread
        // writes to this session too. The decorator serialises writes and drops the
        // connection if a slow client lets the buffer grow past the limit.
        WebSocketSession concurrent = new ConcurrentWebSocketSessionDecorator(
                session, SEND_TIME_LIMIT_MS, SEND_BUFFER_LIMIT_BYTES);

        session.getAttributes().put(ATTR_USER_ID, user.userId());
        session.getAttributes().put(ATTR_SESSION, concurrent);
        registry.register(user.userId(), concurrent);

        // The token outlives neither the session nor its own exp: the client reconnects
        // with a fresh one and catches up over REST, so revocation takes effect within
        // one access-token lifetime instead of lasting as long as the tab stays open.
        if (user.expiresAt() != null) {
            session.getAttributes().put(ATTR_EXPIRY,
                    taskScheduler.schedule(() -> close(session, TOKEN_EXPIRED), user.expiresAt()));
        }

        send(concurrent, ServerFrame.ready(user.userId()));
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
        cancel(session, ATTR_AUTH_TIMEOUT);
        cancel(session, ATTR_EXPIRY);

        Long userId = (Long) session.getAttributes().get(ATTR_USER_ID);
        WebSocketSession concurrent = (WebSocketSession) session.getAttributes().get(ATTR_SESSION);

        if (userId != null && concurrent != null) {
            registry.unregister(userId, concurrent);
        }
    }

    private void send(WebSocketSession session, ServerFrame frame) {
        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(frame)));
        } catch (IOException e) {
            log.debug("Failed to send frame to session {}: {}", session.getId(), e.getMessage());
        }
    }

    private void close(WebSocketSession session, CloseStatus status) {
        try {
            session.close(status);
        } catch (IOException e) {
            log.debug("Failed to close session {}: {}", session.getId(), e.getMessage());
        }
    }

    private void cancel(WebSocketSession session, String attribute) {
        Object task = session.getAttributes().remove(attribute);
        if (task instanceof ScheduledFuture<?> future) {
            future.cancel(false);
        }
    }

    @EventListener(ContextClosedEvent.class)
    public void closeSessionsOnShutdown() {
        registry.allSessions().forEach(session -> close(session, CloseStatus.GOING_AWAY));
    }
}