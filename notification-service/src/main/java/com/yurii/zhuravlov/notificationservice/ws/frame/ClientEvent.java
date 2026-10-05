package com.yurii.zhuravlov.notificationservice.ws.frame;

import java.time.Instant;

public record ClientEvent(
        long eventId,
        long conversationId,
        Instant occurredAt,
        String type,
        Object payload
) {}