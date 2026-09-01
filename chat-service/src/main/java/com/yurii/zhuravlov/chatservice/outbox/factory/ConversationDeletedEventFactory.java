package com.yurii.zhuravlov.chatservice.outbox.factory;

import com.yurii.zhuravlov.chatservice.outbox.payload.ConversationDeletedPayload;
import com.yurii.zhuravlov.contracts.chat.ConversationDeleted;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class ConversationDeletedEventFactory
        extends AbstractChatEventFactory<ConversationDeletedPayload> {

    public ConversationDeletedEventFactory(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    public String eventType() {
        return ConversationDeleted.class.getSimpleName();
    }

    @Override
    public Class<ConversationDeletedPayload> payloadType() {
        return ConversationDeletedPayload.class;
    }

    @Override
    protected Object toAvro(ConversationDeletedPayload p) {
        return ConversationDeleted.newBuilder().build();
    }
}