package com.yurii.zhuravlov.authservice.outbox;

import com.yurii.zhuravlov.authservice.IntegrationTestBase;
import com.yurii.zhuravlov.authservice.dto.requests.RegistrationRequest;
import com.yurii.zhuravlov.authservice.repo.OutboxEventRepository;
import com.yurii.zhuravlov.authservice.service.UserService;
import com.yurii.zhuravlov.contracts.user.UserRegistered;
import org.apache.avro.specific.SpecificRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OutboxPublisherIntegrationTest extends IntegrationTestBase {

    @Autowired
    OutboxPublisher outboxPublisher;
    @Autowired
    UserService userService;
    @Autowired
    OutboxEventRepository outboxRepository;

    @MockitoBean
    KafkaTemplate<String, SpecificRecord> kafkaTemplate;

    @Test
    void shouldMarkEventPublishedOnSuccess() {
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        userService.register(new RegistrationRequest("vasyl", "password123"));

        outboxPublisher.publishPending();

        assertThat(outboxRepository.findAll())
                .singleElement()
                .satisfies(e -> {
                    assertThat(e.getPublishedAt()).isNotNull();
                    assertThat(e.getAttempts()).isZero();
                    assertThat(e.getLastError()).isNull();
                });
    }

    @Test
    void shouldSendWithUserIdAsKey() {
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        userService.register(new RegistrationRequest("vasyl", "password123"));
        Long userId = userRepository.findByUsernameIgnoreCase("vasyl").orElseThrow().getId();

        outboxPublisher.publishPending();

        ArgumentCaptor<SpecificRecord> captor = ArgumentCaptor.forClass(SpecificRecord.class);
        verify(kafkaTemplate).send(eq("user-events"), eq(userId.toString()), captor.capture());

        UserRegistered sent = (UserRegistered) captor.getValue();
        assertThat(sent.getUserId()).isEqualTo(userId);
        assertThat(sent.getUsername()).isEqualTo("vasyl");
    }

    @Test
    void shouldIncrementAttemptsOnFailure() {
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

        userService.register(new RegistrationRequest("vasyl", "password123"));

        outboxPublisher.publishPending();

        assertThat(outboxRepository.findAll())
                .singleElement()
                .satisfies(e -> {
                    assertThat(e.getPublishedAt()).isNull();
                    assertThat(e.getAttempts()).isEqualTo(1);
                    assertThat(e.getLastError()).contains("broker down");
                });
    }

    @Test
    void shouldRetryFailedEventOnNextRun() {
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")))
                .thenReturn(CompletableFuture.completedFuture(null));

        userService.register(new RegistrationRequest("vasyl", "password123"));

        outboxPublisher.publishPending();
        outboxPublisher.publishPending();

        assertThat(outboxRepository.findAll())
                .singleElement()
                .satisfies(e -> {
                    assertThat(e.getPublishedAt()).isNotNull();
                    assertThat(e.getAttempts()).isEqualTo(1);
                });
    }

    @Test
    void shouldStopRetryingAfterMaxAttempts() {
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

        userService.register(new RegistrationRequest("vasyl", "password123"));

        for (int i = 0; i < 10; i++) {
            outboxPublisher.publishPending();
        }

        assertThat(outboxRepository.findAll())
                .singleElement()
                .satisfies(e -> assertThat(e.getAttempts()).isEqualTo(5));   // maxAttempts

        verify(kafkaTemplate, times(5)).send(anyString(), anyString(), any());
    }

    @Test
    void shouldNotPublishAlreadyPublishedEvents() {
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        userService.register(new RegistrationRequest("vasyl", "password123"));

        outboxPublisher.publishPending();
        outboxPublisher.publishPending();

        verify(kafkaTemplate, times(1)).send(anyString(), anyString(), any());
    }

    @Test
    void shouldDoNothingWhenOutboxIsEmpty() {
        outboxPublisher.publishPending();

        verifyNoInteractions(kafkaTemplate);
    }
}