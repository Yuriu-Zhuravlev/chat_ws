package com.yurii.zhuravlov.chatservice.outbox;

import com.yurii.zhuravlov.chatservice.IntegrationTestBase;
import com.yurii.zhuravlov.chatservice.dto.request.CreateConversationRequest;
import com.yurii.zhuravlov.chatservice.service.ConversationService;
import org.apache.avro.specific.SpecificRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.support.SendResult;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class OutboxPublisherIntegrationTest extends IntegrationTestBase {

    @Autowired
    ConversationService conversationService;
    @Autowired
    OutboxPublisher outboxPublisher;

    @BeforeEach
    void givenConversation() {
        givenUser(1L, "alice");
        givenUser(2L, "bob");
        conversationService.create(1L, new CreateConversationRequest("chat", Set.of(2L)));
    }

    @Test
    void publishesPendingAndMarksThemDone() {
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(completedSend());

        outboxPublisher.publishPending();

        assertThat(outboxRepository.findAll())
                .allSatisfy(e -> assertThat(e.getPublishedAt()).isNotNull());
    }

    @Test
    void keyIsTheConversationIdSoOrderingHolds() {
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(completedSend());
        Long conversationId = conversationRepository.findAll().getFirst().getId();

        outboxPublisher.publishPending();

        verify(kafkaTemplate).send(eq("chat-events"), eq(conversationId.toString()), any());
    }

    /**
     * A failed send must not let later events of the same conversation through:
     * the retry would deliver them out of order.
     */
    @Test
    void failureBlocksLaterEventsOfTheSameConversation() {
        Long conversationId = conversationRepository.findAll().getFirst().getId();
        conversationService.rename(conversationId, 1L, "renamed");
        assertThat(outboxRepository.findAll()).hasSize(2);

        when(kafkaTemplate.send(any(), any(), any()))
                .thenThrow(new KafkaException("broker down"));

        outboxPublisher.publishPending();

        assertThat(outboxRepository.findAll())
                .allSatisfy(e -> assertThat(e.getPublishedAt()).isNull());
        assertThat(outboxRepository.findAll())
                .filteredOn(e -> e.getAttempts() > 0)
                .hasSize(1);   // only the first one was actually attempted
    }

    @Test
    void stopsRetryingAfterMaxAttempts() {
        jdbcTemplate.update("UPDATE chat_schema.outbox_events SET attempts = 5");

        outboxPublisher.publishPending();

        verifyNoInteractions(kafkaTemplate);
    }

    private CompletableFuture<SendResult<String, SpecificRecord>> completedSend() {
        return CompletableFuture.completedFuture(null);
    }
}