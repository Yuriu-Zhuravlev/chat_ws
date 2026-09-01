package com.yurii.zhuravlov.chatservice.dto.projection;

import com.yurii.zhuravlov.chatservice.dto.response.ConversationSummaryResponse;
import com.yurii.zhuravlov.chatservice.dto.response.UserResponse;

import java.time.Instant;
import java.util.List;

public record ConversationSummaryRow(
        Long id,
        String title,
        Long adminId,
        Instant lastMessageAt,
        long unreadCount,
        long otherParticipantCount
) {
    public ConversationSummaryResponse toResponse(List<UserResponse> preview) {
        return new ConversationSummaryResponse(
                id, title, adminId, lastMessageAt, unreadCount, otherParticipantCount, preview);
    }
}