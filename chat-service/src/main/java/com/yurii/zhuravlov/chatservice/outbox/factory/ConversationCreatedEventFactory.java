package com.yurii.zhuravlov.chatservice.outbox.factory;

import com.yurii.zhuravlov.chatservice.outbox.payload.ConversationCreatedPayload;
import com.yurii.zhuravlov.contracts.chat.ConversationCreated;
import com.yurii.zhuravlov.contracts.chat.ParticipantInfo;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class ConversationCreatedEventFactory
        extends AbstractChatEventFactory<ConversationCreatedPayload> {

    public ConversationCreatedEventFactory(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    public String eventType() {
        return ConversationCreated.class.getSimpleName();
    }

    @Override
    public Class<ConversationCreatedPayload> payloadType() {
        return ConversationCreatedPayload.class;
    }

    @Override
    protected Object toAvro(ConversationCreatedPayload p) {
        return ConversationCreated.newBuilder()
                .setTitle(p.title())
                .setAdminId(p.adminId())
                .setParticipants(p.participants().stream()
                        .map(i -> ParticipantInfo.newBuilder()
                                .setUserId(i.userId())
                                .setUsername(i.username())
                                .build())
                        .toList())
                .build();
    }
}