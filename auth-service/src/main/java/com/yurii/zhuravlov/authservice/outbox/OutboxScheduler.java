package com.yurii.zhuravlov.authservice.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.kafka.outbox.scheduling-enabled",
                       havingValue = "true", matchIfMissing = true)
public class OutboxScheduler {

    private final OutboxPublisher publisher;

    @Scheduled(fixedDelayString = "${app.kafka.outbox.poll-interval}")
    public void run() {
        publisher.publishPending();
    }

    @Scheduled(cron = "0 0 3 * * *")
    public void cleanup(){
        publisher.cleanupPublished();
    }
}