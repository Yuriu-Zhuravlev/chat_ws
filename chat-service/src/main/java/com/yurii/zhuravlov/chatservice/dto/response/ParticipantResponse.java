package com.yurii.zhuravlov.chatservice.dto.response;

import java.time.Instant;

public record ParticipantResponse(
        Long userId,
        String username,
        Long lastReadMessageId,   // null when the participant has read nothing yet
        Instant joinedAt
) {}