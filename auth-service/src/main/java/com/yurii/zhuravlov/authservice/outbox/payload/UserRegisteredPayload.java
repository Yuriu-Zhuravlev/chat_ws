package com.yurii.zhuravlov.authservice.outbox.payload;

import java.time.Instant;

public record UserRegisteredPayload(Long userId, String username, Instant occurredAt) {}