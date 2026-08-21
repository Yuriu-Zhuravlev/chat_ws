package com.yurii.zhuravlov.authservice.outbox;

import com.yurii.zhuravlov.authservice.config.properties.KafkaTopicProperties;
import com.yurii.zhuravlov.authservice.config.properties.OutboxProperties;
import com.yurii.zhuravlov.authservice.dto.UserRegisteredPayload;
import com.yurii.zhuravlov.authservice.entities.OutboxEvent;
import com.yurii.zhuravlov.authservice.repo.OutboxEventRepository;
import com.yurii.zhuravlov.contracts.user.UserRegistered;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.specific.SpecificRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {

    private final OutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, SpecificRecord> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final OutboxProperties properties;
    private final KafkaTopicProperties topics;

    @Transactional
    public void publishPending() {
        List<OutboxEvent> batch = outboxRepository.findUnpublishedForUpdate(
                properties.maxAttempts(), properties.batchSize());

        if (batch.isEmpty()) {
            return;
        }

        for (OutboxEvent event : batch) {
            try {
                UserRegisteredPayload payload =
                        objectMapper.readValue(event.getPayload(), UserRegisteredPayload.class);

                UserRegistered avro = UserRegistered.newBuilder()
                        .setUserId(payload.userId())
                        .setUsername(payload.username())
                        .setOccurredAt(payload.occurredAt())
                        .build();

                kafkaTemplate.send(topics.userEvents(), payload.userId().toString(), avro).get();

                event.setPublishedAt(Instant.now());

            } catch (Exception e) {
                event.setAttempts(event.getAttempts() + 1);
                event.setLastError(truncate(e.getMessage()));
                log.warn("Failed to publish outbox event id={}, attempt={}",
                        event.getId(), event.getAttempts(), e);
            }
        }
    }

    @Transactional
    public void cleanupPublished() {
        int deleted = outboxRepository.deletePublishedBefore(
                Instant.now().minus(Duration.ofDays(7)));
        if (deleted > 0) {
            log.info("Cleaned up {} published outbox events", deleted);
        }
    }

    private String truncate(String message) {
        if (message == null) return "unknown";
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }
}