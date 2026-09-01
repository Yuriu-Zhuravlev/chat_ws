package com.yurii.zhuravlov.chatservice.outbox.factory;

import com.yurii.zhuravlov.chatservice.entities.OutboxEvent;
import com.yurii.zhuravlov.chatservice.outbox.payload.ChatEventPayload;
import com.yurii.zhuravlov.contracts.chat.ChatEvent;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@RequiredArgsConstructor
public abstract class AbstractChatEventFactory<P extends ChatEventPayload>
        implements ChatEventFactory {

    private final ObjectMapper objectMapper;

    protected abstract Object toAvro(P payload);

    @SuppressWarnings("unchecked")
    @Override
    public ChatEvent build(OutboxEvent row) {
        P payload = (P) objectMapper.readValue(row.getPayload(), payloadType());

        return ChatEvent.newBuilder()
                .setEventId(row.getId())
                .setConversationId(Long.parseLong(row.getAggregateId()))
                .setOccurredAt(row.getCreatedAt())
                .setRecipients(payload.recipients())
                .setPayload(toAvro(payload))
                .build();
    }
}