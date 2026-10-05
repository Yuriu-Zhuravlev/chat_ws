package com.yurii.zhuravlov.notificationservice.config.properties;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "app.kafka.topics")
@Validated
public record KafkaTopicProperties(
        @NotBlank String chatEvents
) {
}
