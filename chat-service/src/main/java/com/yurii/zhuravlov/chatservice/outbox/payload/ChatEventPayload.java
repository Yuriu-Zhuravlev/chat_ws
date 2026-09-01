package com.yurii.zhuravlov.chatservice.outbox.payload;

import java.util.List;

public interface ChatEventPayload {
    List<Long> recipients();
}