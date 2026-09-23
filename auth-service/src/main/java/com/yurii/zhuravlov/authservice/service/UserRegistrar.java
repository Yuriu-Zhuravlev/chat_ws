package com.yurii.zhuravlov.authservice.service;

import com.yurii.zhuravlov.authservice.entities.OutboxEvent;
import com.yurii.zhuravlov.authservice.entities.User;
import com.yurii.zhuravlov.authservice.outbox.OutboxWakeup;
import com.yurii.zhuravlov.authservice.outbox.payload.UserRegisteredPayload;
import com.yurii.zhuravlov.authservice.repo.OutboxEventRepository;
import com.yurii.zhuravlov.authservice.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class UserRegistrar {

    private final UserRepository userRepository;
    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<OutboxWakeup> wakeup;

    @Transactional
    public void createWithEvent(String username, String passwordHash) {
        User user = userRepository.saveAndFlush(new User(passwordHash, username));

        outboxRepository.save(OutboxEvent.builder()
                .aggregateType("User")
                .aggregateId(user.getId().toString())
                .eventType("UserRegistered")
                .payload(toJson(new UserRegisteredPayload(
                        user.getId(), user.getUsername(), Instant.now())))
                .build());

        wakeup.ifAvailable(OutboxWakeup::publishAfterCommit);
    }

    private String toJson(UserRegisteredPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize outbox payload", e);
        }
    }
}