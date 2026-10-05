package com.yurii.zhuravlov.notificationservice.ws;

import com.yurii.zhuravlov.contracts.chat.ChatEvent;
import com.yurii.zhuravlov.notificationservice.ws.frame.ServerFrame;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class EventDispatcher {

    private final SessionRegistry registry;
    private final ChatEventMapper mapper;
    private final ObjectMapper objectMapper;

    public void dispatch(ChatEvent event) {
        // Serialised once, not per session: a group of a hundred would otherwise pay
        // a hundred identical serialisations for one event.
        TextMessage message = new TextMessage(
                objectMapper.writeValueAsString(ServerFrame.event(mapper.toClientEvent(event))));

        for (Long recipient : event.getRecipients()) {
            for (WebSocketSession session : registry.sessionsOf(recipient)) {
                send(session, message);
            }
        }
    }

    /**
     * A session can close between the registry lookup and the write, and one dead peer
     * must not stop delivery to the rest. Debug level on purpose: this is expected noise,
     * not an incident.
     */
    private void send(WebSocketSession session, TextMessage message) {
        try {
            session.sendMessage(message);
        } catch (IOException | IllegalStateException e) {
            log.debug("Delivery to session {} failed: {}", session.getId(), e.getMessage());
        }
    }
}