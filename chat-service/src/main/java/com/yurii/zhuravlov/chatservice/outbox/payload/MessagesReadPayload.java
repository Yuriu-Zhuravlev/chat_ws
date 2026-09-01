package com.yurii.zhuravlov.chatservice.outbox.payload;

import java.util.List;

public record MessagesReadPayload(
        List<Long> recipients, Long readerId, Long lastReadMessageId
) implements ChatEventPayload {}