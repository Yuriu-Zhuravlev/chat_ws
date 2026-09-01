package com.yurii.zhuravlov.chatservice.outbox.payload;

import java.util.List;

public record ParticipantAddedPayload(
        List<Long> recipients, Long userId, String username
) implements ChatEventPayload {}