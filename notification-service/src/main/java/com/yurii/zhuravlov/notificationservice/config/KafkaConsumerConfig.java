package com.yurii.zhuravlov.notificationservice.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@Slf4j
public class KafkaConsumerConfig {

    /**
     * No retries and no dead-letter topic. Delivery here is best-effort and the client's
     * recovery path is chat's REST catch-up, not Kafka — so a parked record would never be
     * replayed, and retrying only delays every later event on the partition. What matters
     * is the signal: a bad record means chat and notification disagree on the contract,
     * and the counter makes that alertable.
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(MeterRegistry meterRegistry) {
        Counter dropped = Counter.builder("notification.events.dropped")
                .description("Chat events skipped because they could not be processed")
                .register(meterRegistry);

        return new DefaultErrorHandler(
                (record, exception) -> {
                    dropped.increment();
                    log.error("Dropping event at {}-{}@{}: {}",
                            record.topic(), record.partition(), record.offset(),
                            exception.getMessage(), exception);
                },
                new FixedBackOff(0L, 0L));
    }
}