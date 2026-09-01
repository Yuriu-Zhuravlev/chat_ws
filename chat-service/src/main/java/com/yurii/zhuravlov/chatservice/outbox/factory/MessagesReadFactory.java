package com.yurii.zhuravlov.chatservice.outbox.factory;

import com.yurii.zhuravlov.chatservice.outbox.payload.MessagesReadPayload;
import com.yurii.zhuravlov.contracts.chat.MessagesRead;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class MessagesReadFactory extends AbstractChatEventFactory<MessagesReadPayload>{
    public MessagesReadFactory(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    protected Object toAvro(MessagesReadPayload payload) {
        return MessagesRead.newBuilder()
                .setLastReadMessageId(payload.lastReadMessageId())
                .setReaderId(payload.readerId())
                .build();
    }

    @Override
    public String eventType() {
        return MessagesRead.class.getSimpleName();
    }

    @Override
    public Class<MessagesReadPayload> payloadType() {
        return MessagesReadPayload.class;
    }
}
