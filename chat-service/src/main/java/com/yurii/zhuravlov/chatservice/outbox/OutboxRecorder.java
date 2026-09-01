package com.yurii.zhuravlov.chatservice.outbox;

import com.yurii.zhuravlov.chatservice.entities.OutboxEvent;
import com.yurii.zhuravlov.chatservice.outbox.payload.ChatEventPayload;
import com.yurii.zhuravlov.chatservice.outbox.factory.ChatEventFactories;
import com.yurii.zhuravlov.chatservice.repo.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class OutboxRecorder {

    private static final String AGGREGATE_TYPE = "Conversation";

    private final OutboxEventRepository outboxRepository;
    private final ChatEventFactories factories;
    private final ObjectMapper objectMapper;

    /**
     * Must be called inside the same transaction as the domain write:
     * that is the whole point of the outbox pattern.
     */
    public void record(Long conversationId, ChatEventPayload payload) {
        outboxRepository.save(OutboxEvent.builder()
                .aggregateType(AGGREGATE_TYPE)
                .aggregateId(conversationId.toString())
                .eventType(factories.eventTypeOf(payload.getClass()))
                .payload(objectMapper.writeValueAsString(payload))
                .build());
    }
}