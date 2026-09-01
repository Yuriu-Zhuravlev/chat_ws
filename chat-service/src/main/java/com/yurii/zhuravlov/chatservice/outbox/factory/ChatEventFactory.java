package com.yurii.zhuravlov.chatservice.outbox.factory;

import com.yurii.zhuravlov.chatservice.entities.OutboxEvent;
import com.yurii.zhuravlov.chatservice.outbox.payload.ChatEventPayload;
import com.yurii.zhuravlov.contracts.chat.ChatEvent;

public interface ChatEventFactory {

    String eventType();

    Class<? extends ChatEventPayload> payloadType();

    ChatEvent build(OutboxEvent row);
}