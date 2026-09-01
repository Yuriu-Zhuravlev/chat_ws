package com.yurii.zhuravlov.chatservice.outbox.factory;

import com.yurii.zhuravlov.chatservice.outbox.payload.ConversationUpdatedPayload;
import com.yurii.zhuravlov.contracts.chat.ConversationUpdated;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class ConversationUpdatedFactory extends AbstractChatEventFactory<ConversationUpdatedPayload>{
    public ConversationUpdatedFactory(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    protected Object toAvro(ConversationUpdatedPayload payload) {
        return ConversationUpdated.newBuilder()
                .setAdminId(payload.adminId())
                .setTitle(payload.title())
                .build();
    }

    @Override
    public String eventType() {
        return ConversationUpdated.class.getSimpleName();
    }

    @Override
    public Class<ConversationUpdatedPayload> payloadType() {
        return ConversationUpdatedPayload.class;
    }
}
