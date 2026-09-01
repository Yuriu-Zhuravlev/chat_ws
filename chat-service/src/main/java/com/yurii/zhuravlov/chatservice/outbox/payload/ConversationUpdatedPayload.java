package com.yurii.zhuravlov.chatservice.outbox.payload;

import java.util.List;

public record ConversationUpdatedPayload(
        List<Long> recipients, String title, Long adminId
) implements ChatEventPayload {}