package com.yurii.zhuravlov.chatservice.config.properties;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.kafka.outbox")
@Validated
public record OutboxProperties(
        @Min(1) int maxAttempts,
        @Min(1) int batchSize,
        @NotNull Duration pollInterval
        ) {
}
