package com.yurii.zhuravlov.chatservice.dto.response;

import java.time.Instant;
import java.util.List;

public record ConversationSummaryResponse(
        Long id,
        String title,
        Long adminId,
        Instant lastMessageAt,
        long unreadCount,
        long participantCount,
        List<UserResponse> preview      // перші кілька, для аватарок і підпису
) {}