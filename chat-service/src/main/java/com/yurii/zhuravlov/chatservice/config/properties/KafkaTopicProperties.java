package com.yurii.zhuravlov.chatservice.config.properties;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "app.kafka.topics")
@Validated
public record KafkaTopicProperties(
        @NotBlank String userEvents,
        @NotBlank String chatEvents
) {
}
