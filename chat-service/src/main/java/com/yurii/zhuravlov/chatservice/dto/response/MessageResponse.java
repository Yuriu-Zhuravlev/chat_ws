package com.yurii.zhuravlov.chatservice.dto.response;

import java.time.Instant;

public record MessageResponse(
        Long id, Long conversationId, Long senderId,
        String clientMessageId, String content, Instant createdAt
) {}