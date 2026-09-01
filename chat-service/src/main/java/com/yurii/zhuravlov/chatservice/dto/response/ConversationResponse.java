package com.yurii.zhuravlov.chatservice.dto.response;

import java.time.Instant;
import java.util.List;

public record ConversationResponse(
        Long id,
        String title,
        Long adminId,
        List<UserResponse> participants,
        Instant createdAt,
        Instant lastMessageAt
) {}