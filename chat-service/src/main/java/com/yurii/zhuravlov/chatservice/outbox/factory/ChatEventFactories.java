package com.yurii.zhuravlov.chatservice.outbox.factory;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ChatEventFactories {

    private final List<ChatEventFactory> factories;

    private final Map<String, ChatEventFactory> byEventType = new HashMap<>();
    private final Map<Class<?>, String> eventTypeByPayload = new HashMap<>();

    /**
     * Duplicate registrations would silently shadow each other and produce rows
     * that no factory can read back, so fail at startup instead.
     */
    @PostConstruct
    void index() {
        for (ChatEventFactory factory : factories) {
            if (byEventType.put(factory.eventType(), factory) != null) {
                throw new IllegalStateException(
                        "Duplicate ChatEventFactory for " + factory.eventType());
            }
            eventTypeByPayload.put(factory.payloadType(), factory.eventType());
        }
    }

    public ChatEventFactory forEventType(String eventType) {
        ChatEventFactory factory = byEventType.get(eventType);
        if (factory == null) {
            throw new IllegalStateException("No ChatEventFactory for type " + eventType);
        }
        return factory;
    }

    public String eventTypeOf(Class<?> payloadType) {
        String eventType = eventTypeByPayload.get(payloadType);
        if (eventType == null) {
            throw new IllegalStateException("No ChatEventFactory for payload " + payloadType);
        }
        return eventType;
    }
}