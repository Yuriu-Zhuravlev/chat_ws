package com.yurii.zhuravlov.notificationservice.ws;

import org.jspecify.annotations.NonNull;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class RecordingWebSocketClient extends TextWebSocketHandler implements AutoCloseable {

    private final BlockingQueue<String> frames = new LinkedBlockingQueue<>();
    private final CompletableFuture<CloseStatus> closed = new CompletableFuture<>();
    private final WebSocketSession session;

    public RecordingWebSocketClient(int port) throws Exception {
        this.session = new StandardWebSocketClient()
                .execute(this, "ws://localhost:" + port + "/ws")
                .get(5, TimeUnit.SECONDS);
    }

    @Override
    protected void handleTextMessage(@NonNull WebSocketSession session, TextMessage message) {
        frames.add(message.getPayload());
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
        closed.complete(status);
    }

    public void send(String payload) throws IOException {
        session.sendMessage(new TextMessage(payload));
    }

    public void authenticate(String token) throws Exception {
        send("{\"type\":\"AUTH\",\"token\":\"%s\"}".formatted(token));
        assertThat(nextFrame()).contains("\"type\":\"READY\"");
    }

    /** Null when nothing arrives in time — used both to await a frame and to prove silence. */
    public String nextFrame() throws InterruptedException {
        return frames.poll(5, TimeUnit.SECONDS);
    }

    public String nextFrameWithin(Duration timeout) throws InterruptedException {
        return frames.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    public CloseStatus awaitClose(Duration timeout) throws Exception {
        return closed.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public void close() throws IOException {
        if (session.isOpen()) {
            session.close();
        }
    }
}