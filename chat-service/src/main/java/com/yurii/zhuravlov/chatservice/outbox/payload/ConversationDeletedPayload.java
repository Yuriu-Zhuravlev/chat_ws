package com.yurii.zhuravlov.chatservice.outbox.payload;

import java.util.List;

public record ConversationDeletedPayload(
        List<Long> recipients
) implements ChatEventPayload {}