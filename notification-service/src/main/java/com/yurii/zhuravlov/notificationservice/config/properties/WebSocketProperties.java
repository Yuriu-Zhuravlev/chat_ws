package com.yurii.zhuravlov.notificationservice.config.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.ws")
@Validated
public record WebSocketProperties(
        @NotBlank String path,

        @NotNull Duration authTimeout
) {
}
