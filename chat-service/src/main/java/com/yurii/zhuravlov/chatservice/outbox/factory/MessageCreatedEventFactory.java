package com.yurii.zhuravlov.chatservice.outbox.factory;

import com.yurii.zhuravlov.chatservice.outbox.payload.MessageCreatedPayload;
import com.yurii.zhuravlov.contracts.chat.MessageCreated;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class MessageCreatedEventFactory
        extends AbstractChatEventFactory<MessageCreatedPayload> {

    public MessageCreatedEventFactory(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    public String eventType() {
        return MessageCreated.class.getSimpleName();
    }

    @Override
    public Class<MessageCreatedPayload> payloadType() {
        return MessageCreatedPayload.class;
    }

    @Override
    protected Object toAvro(MessageCreatedPayload p) {
        return MessageCreated.newBuilder()
                .setMessageId(p.messageId())
                .setSenderId(p.senderId())
                .setClientMessageId(p.clientMessageId())
                .setContent(p.content())
                .setCreatedAt(p.createdAt())
                .build();
    }
}