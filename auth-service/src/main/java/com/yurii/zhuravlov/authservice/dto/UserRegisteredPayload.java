package com.yurii.zhuravlov.authservice.dto;

import java.time.Instant;

public record UserRegisteredPayload(Long userId, String username, Instant occurredAt) {}