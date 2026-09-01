package com.yurii.zhuravlov.chatservice.outbox;

import com.yurii.zhuravlov.chatservice.config.properties.KafkaTopicProperties;
import com.yurii.zhuravlov.chatservice.config.properties.OutboxProperties;
import com.yurii.zhuravlov.chatservice.entities.OutboxEvent;
import com.yurii.zhuravlov.chatservice.outbox.factory.ChatEventFactories;
import com.yurii.zhuravlov.chatservice.repo.OutboxEventRepository;
import com.yurii.zhuravlov.contracts.chat.ChatEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.specific.SpecificRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {

    private final OutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, SpecificRecord> kafkaTemplate;
    private final OutboxProperties properties;
    private final KafkaTopicProperties topics;
    private final ChatEventFactories factories;

    @Transactional
    public void publishPending() {
        List<OutboxEvent> batch = outboxRepository.findUnpublishedForUpdate(
                properties.maxAttempts(), properties.batchSize());

        if (batch.isEmpty()) {
            return;
        }
        Set<String> blocked = new HashSet<>();

        for (OutboxEvent event : batch) {
            if (blocked.contains(event.getAggregateId())) {
                continue;
            }
            try {
                ChatEvent avro = factories.forEventType(event.getEventType()).build(event);
                kafkaTemplate.send(topics.chatEvents(), event.getAggregateId(), avro).get();
                event.setPublishedAt(Instant.now());

            } catch (Exception e) {
                event.setAttempts(event.getAttempts() + 1);
                event.setLastError(truncate(e.getMessage()));
                blocked.add(event.getAggregateId());
                log.warn("Failed to publish outbox event id={}, type={}, attempt={}",
                        event.getId(), event.getEventType(), event.getAttempts(), e);
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