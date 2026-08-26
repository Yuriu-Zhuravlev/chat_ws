package com.yurii.zhuravlov.chatservice.consumer;

import com.yurii.zhuravlov.chatservice.service.UserProjectionService;
import com.yurii.zhuravlov.contracts.user.UserRegistered;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserEventsConsumer {

    private final UserProjectionService projectionService;

    @KafkaListener(
            topics = "${app.kafka.topics.user-events}",
            groupId = "${spring.kafka.consumer.group-id}")
    public void onUserRegistered(UserRegistered event) {
        log.debug("Received UserRegistered: userId={}", event.getUserId());
        projectionService.upsert(event.getUserId(), event.getUsername());
    }
}