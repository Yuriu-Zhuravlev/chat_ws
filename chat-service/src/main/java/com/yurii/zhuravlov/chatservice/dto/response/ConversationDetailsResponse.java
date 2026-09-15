package com.yurii.zhuravlov.chatservice.dto.response;

import java.time.Instant;
import java.util.List;

public record ConversationDetailsResponse(
        Long id,
        String title,
        Long adminId,
        Instant createdAt,
        Instant lastMessageAt,
        List<ParticipantResponse> participants
) {}