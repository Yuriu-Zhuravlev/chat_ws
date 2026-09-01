package com.yurii.zhuravlov.chatservice.outbox.payload;

import java.util.List;

public record ParticipantRemovedPayload(
        List<Long> recipients, Long userId, boolean selfLeave
) implements ChatEventPayload {}