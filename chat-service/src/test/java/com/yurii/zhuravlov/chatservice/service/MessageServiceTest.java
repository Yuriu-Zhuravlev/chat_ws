package com.yurii.zhuravlov.chatservice.service;

import com.yurii.zhuravlov.chatservice.IntegrationTestBase;
import com.yurii.zhuravlov.chatservice.dto.request.CreateConversationRequest;
import com.yurii.zhuravlov.chatservice.dto.request.MessagePageRequest;
import com.yurii.zhuravlov.chatservice.dto.request.SendMessageRequest;
import com.yurii.zhuravlov.chatservice.dto.response.MessagePageResponse;
import com.yurii.zhuravlov.chatservice.dto.response.MessageResponse;
import com.yurii.zhuravlov.chatservice.dto.response.ParticipantResponse;
import com.yurii.zhuravlov.chatservice.dto.response.SendResult;
import com.yurii.zhuravlov.chatservice.entities.Conversation;
import com.yurii.zhuravlov.chatservice.outbox.payload.MessageCreatedPayload;
import com.yurii.zhuravlov.chatservice.outbox.payload.MessagesReadPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MessageServiceTest extends IntegrationTestBase {

    @Autowired
    ConversationService conversationService;

    private Long conversationId;

    @BeforeEach
    void givenConversation() {
        givenUser(1L, "alice");
        givenUser(2L, "bob");
        conversationId = conversationService
                .create(1L, new CreateConversationRequest("chat", Set.of(2L)))
                .id();
        outboxRepository.deleteAllInBatch();
    }

    private Long send(Long sender, String clientMessageId, String content) {
        return conversationService
                .send(conversationId, sender, new SendMessageRequest(content, clientMessageId))
                .message().id();
    }

    @Nested
    class Sending {

        @Test
        void storesMessageAndNotifiesEveryone() {
            Long messageId = send(1L, "c1", "hello");

            MessageCreatedPayload payload = singlePayload(MessageCreatedPayload.class);
            assertThat(payload.messageId()).isEqualTo(messageId);
            assertThat(payload.senderId()).isEqualTo(1L);
            assertThat(payload.recipients()).containsExactlyInAnyOrder(1L, 2L);
        }

        /** The unique index resolves the retry; no exception, no second row, no second event. */
        @Test
        void retryWithSameClientIdIsIdempotent() {
            SendMessageRequest request = new SendMessageRequest("hello", "c1");

            SendResult first = conversationService.send(conversationId, 1L, request);
            outboxRepository.deleteAllInBatch();
            SendResult second = conversationService.send(conversationId, 1L, request);

            assertThat(first.created()).isTrue();
            assertThat(second.created()).isFalse();
            assertThat(second.message().id()).isEqualTo(first.message().id());
            assertThat(messageRepository.findAll()).hasSize(1);
            assertThat(outboxRepository.findAll()).isEmpty();
        }

        @Test
        void sameClientIdInAnotherConversationIsADifferentMessage() {
            Long other = conversationService
                    .create(1L, new CreateConversationRequest("other", Set.of(2L))).id();

            send(1L, "c1", "hello");
            conversationService.send(other, 1L, new SendMessageRequest("hello", "c1"));

            assertThat(messageRepository.findAll()).hasSize(2);
        }

        @Test
        void updatesLastMessageAt() {
            send(1L, "c1", "hello");

            assertThat(conversationRepository.findById(conversationId))
                    .get().extracting(Conversation::getLastMessageAt).isNotNull();
        }

        @Test
        void rejectsNonParticipants() {
            givenUser(3L, "outsider");

            assertFailsWith(HttpStatus.NOT_FOUND,
                    () -> conversationService.send(conversationId, 3L,
                            new SendMessageRequest("hello", "c1")));
        }
    }

    @Nested
    class UnreadCounters {

        /** Otherwise "1 unread" would hang in your own chat right after you replied. */
        @Test
        void sendingImplicitlyMovesTheSenderMarker() {
            send(2L, "b1", "question");
            send(1L, "a1", "answer");

            assertThat(unreadFor(1L)).isZero();
        }

        @Test
        void countsOnlyMessagesFromOthers() {
            send(1L, "a1", "mine");
            send(2L, "b1", "theirs");
            send(2L, "b2", "theirs again");

            assertThat(unreadFor(1L)).isEqualTo(2);
            assertThat(unreadFor(2L)).isZero();
        }

        private long unreadFor(Long userId) {
            return conversationService.list(userId, 0).getFirst().unreadCount();
        }
    }

    @Nested
    class Reading {

        @Test
        void movesMarkerAndNotifiesEveryone() {
            send(2L, "b1", "one");
            Long second = send(2L, "b2", "two");
            outboxRepository.deleteAllInBatch();

            conversationService.markRead(conversationId, 1L, second);

            MessagesReadPayload payload = singlePayload(MessagesReadPayload.class);
            assertThat(payload.readerId()).isEqualTo(1L);
            assertThat(payload.lastReadMessageId()).isEqualTo(second);
            assertThat(payload.recipients()).containsExactlyInAnyOrder(1L, 2L);
        }

        /** Zero affected rows is the normal duplicate path, not an error. */
        @Test
        void repeatedReadPublishesNothing() {
            Long messageId = send(2L, "b1", "one");
            conversationService.markRead(conversationId, 1L, messageId);
            outboxRepository.deleteAllInBatch();

            conversationService.markRead(conversationId, 1L, messageId);

            assertThat(outboxRepository.findAll()).isEmpty();
        }

        @Test
        void neverMovesBackwards() {
            Long first = send(2L, "b1", "one");
            Long second = send(2L, "b2", "two");

            conversationService.markRead(conversationId, 1L, second);
            conversationService.markRead(conversationId, 1L, first);

            assertThat(conversationService.details(conversationId, 1L).participants())
                    .filteredOn(p -> p.userId().equals(1L))
                    .singleElement()
                    .extracting(ParticipantResponse::lastReadMessageId)
                    .isEqualTo(second);
        }

        /**
         * Without the ownership check a foreign id would sail past the forward-only
         * guard and zero the counter permanently.
         */
        @Test
        void rejectsMessageFromAnotherConversation() {
            Long other = conversationService
                    .create(1L, new CreateConversationRequest("other", Set.of(2L))).id();
            Long foreign = conversationService
                    .send(other, 2L, new SendMessageRequest("hi", "x1")).message().id();

            assertFailsWith(HttpStatus.BAD_REQUEST,
                    () -> conversationService.markRead(conversationId, 1L, foreign));
        }
    }

    @Nested
    class Pagination {

        @BeforeEach
        void givenFiveMessages() {
            for (int i = 1; i <= 5; i++) {
                send(2L, "b" + i, "msg " + i);
            }
        }

        @Test
        void firstPageReturnsNewestInAscendingOrder() {
            MessagePageResponse page = conversationService.messages(conversationId, 1L,
                    new MessagePageRequest(null, null, 2));

            assertThat(page.messages()).extracting(MessageResponse::content)
                    .containsExactly("msg 4", "msg 5");
            assertThat(page.hasMore()).isTrue();
        }

        @Test
        void walksBackwardsThroughHistory() {
            MessagePageResponse first = conversationService.messages(conversationId, 1L,
                    new MessagePageRequest(null, null, 2));
            Long cursor = first.messages().getFirst().id();

            MessagePageResponse second = conversationService.messages(conversationId, 1L,
                    new MessagePageRequest(cursor, true, 2));

            assertThat(second.messages()).extracting(MessageResponse::content)
                    .containsExactly("msg 2", "msg 3");
            assertThat(second.hasMore()).isTrue();
        }

        @Test
        void reportsNoMoreOnTheLastPage() {
            MessagePageResponse page = conversationService.messages(conversationId, 1L,
                    new MessagePageRequest(null, null, 10));

            assertThat(page.messages()).hasSize(5);
            assertThat(page.hasMore()).isFalse();
        }

        /** Catch-up after reconnect: everything newer than the last id the client has. */
        @Test
        void catchesUpForwards() {
            Long third = conversationService.messages(conversationId, 1L,
                    new MessagePageRequest(null, null, 10)).messages().get(2).id();

            MessagePageResponse page = conversationService.messages(conversationId, 1L,
                    new MessagePageRequest(third, false, 10));

            assertThat(page.messages()).extracting(MessageResponse::content)
                    .containsExactly("msg 4", "msg 5");
            assertThat(page.hasMore()).isFalse();
        }
    }
}