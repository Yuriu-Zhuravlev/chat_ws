package com.yurii.zhuravlov.chatservice.outbox.payload;

import java.time.Instant;
import java.util.List;

public record MessageCreatedPayload(
        List<Long> recipients, Long messageId, Long senderId,
        String clientMessageId, String content, Instant createdAt
) implements ChatEventPayload {}