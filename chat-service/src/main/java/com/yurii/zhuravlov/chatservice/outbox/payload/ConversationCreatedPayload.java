package com.yurii.zhuravlov.chatservice.outbox.payload;

import java.util.List;

public record ConversationCreatedPayload(
        List<Long> recipients,
        String title,
        Long adminId,
        List<ParticipantInfoDto> participants
) implements ChatEventPayload {

    public record ParticipantInfoDto(Long userId, String username) {}
}