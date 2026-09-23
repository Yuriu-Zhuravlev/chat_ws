package com.yurii.zhuravlov.chatservice.service;

import com.yurii.zhuravlov.chatservice.IntegrationTestBase;
import com.yurii.zhuravlov.chatservice.dto.request.CreateConversationRequest;
import com.yurii.zhuravlov.chatservice.dto.response.ParticipantResponse;
import com.yurii.zhuravlov.chatservice.outbox.payload.ConversationCreatedPayload;
import com.yurii.zhuravlov.chatservice.outbox.payload.ConversationDeletedPayload;
import com.yurii.zhuravlov.chatservice.outbox.payload.ParticipantRemovedPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationServiceTest extends IntegrationTestBase {
    @Autowired
    ConversationService conversationService;

    @Nested
    class ConversationAccessIntegrationTest {

        private Long conversationId;

        @BeforeEach
        void givenConversation() {
            givenUser(1L, "admin");
            givenUser(2L, "member");
            givenUser(3L, "outsider");

            conversationId = conversationService
                    .create(1L, new CreateConversationRequest("chat", Set.of(2L)))
                    .id();
        }

        /** 404 rather than 403: a 403 would confirm the conversation exists to strangers. */
        @Test
        void hidesConversationFromNonParticipants() {
            assertFailsWith(HttpStatus.NOT_FOUND, () -> conversationService.details(conversationId, 3L));
        }

        @Test
        void hidesMissingConversationTheSameWay() {
            assertFailsWith(HttpStatus.NOT_FOUND, () -> conversationService.details(999_999L, 1L));
        }

        /** Already a participant, so there is nothing left to hide: 403. */
        @Test
        void forbidsDestructiveActionsForNonAdmin() {
            assertFailsWith(HttpStatus.FORBIDDEN, () -> conversationService.removeParticipant(conversationId, 2L, 1L));

            assertFailsWith(HttpStatus.FORBIDDEN, () -> conversationService.transferAdmin(conversationId, 2L, 2L));

            assertFailsWith(HttpStatus.FORBIDDEN, () -> conversationService.delete(conversationId, 2L));

        }

        @Test
        void allowsNeutralActionsForAnyParticipant() {
            conversationService.rename(conversationId, 2L, "renamed by member");
            conversationService.addParticipant(conversationId, 2L, 3L);

            assertThat(conversationService.details(conversationId, 2L))
                    .satisfies(d -> {
                        assertThat(d.title()).isEqualTo("renamed by member");
                        assertThat(d.participants()).hasSize(3);
                    });
        }
    }

    @Nested
    class ConversationAdminIntegrationTest{

        private Long conversationId;

        @BeforeEach
        void givenConversation() {
            givenUser(1L, "admin");
            givenUser(2L, "member");
            conversationId = conversationService
                    .create(1L, new CreateConversationRequest("chat", Set.of(2L)))
                    .id();
        }

        /** Keeps the invariant: admin_id always points at an existing participant. */
        @Test
        void adminCannotLeaveWithoutTransferring() {
            assertFailsWith(HttpStatus.CONFLICT, () -> conversationService.leave(conversationId, 1L));
        }

        @Test
        void adminCanLeaveAfterTransferring() {
            conversationService.transferAdmin(conversationId, 1L, 2L);
            conversationService.leave(conversationId, 1L);

            assertThat(conversationService.details(conversationId, 2L))
                    .satisfies(d -> {
                        assertThat(d.adminId()).isEqualTo(2L);
                        assertThat(d.participants()).extracting(ParticipantResponse::userId)
                                .containsExactly(2L);
                    });
        }

        @Test
        void adminCannotBeRemoved() {
            assertFailsWith(HttpStatus.CONFLICT, () -> conversationService.removeParticipant(conversationId, 1L, 1L));
        }

        @Test
        void cannotTransferToNonParticipant() {
            givenUser(3L, "outsider");
            assertFailsWith(HttpStatus.BAD_REQUEST, () -> conversationService.transferAdmin(conversationId, 1L, 3L));
        }
    }

    @Nested
    class ConversationEventsIntegrationTest{

        private Long conversationId;

        @BeforeEach
        void givenConversation() {
            givenUser(1L, "admin");
            givenUser(2L, "member");
            givenUser(3L, "third");
            conversationId = conversationService
                    .create(1L, new CreateConversationRequest("chat", Set.of(2L)))
                    .id();
        }

        /** Every event goes to every participant: a second browser tab must learn about it too. */
        @Test
        void creationNotifiesTheCreatorAsWell() {
            ConversationCreatedPayload payload = singlePayload(ConversationCreatedPayload.class);

            assertThat(payload.recipients()).containsExactlyInAnyOrder(1L, 2L);
            assertThat(payload.participants()).hasSize(2);
        }

        /**
         * Regression: the snapshot must be taken before the delete, otherwise the removed
         * user never learns they are gone and their tab hangs on a dead conversation.
         */
        @Test
        void removalNotifiesTheRemovedUser() {
            conversationService.addParticipant(conversationId, 1L, 3L);
            outboxRepository.deleteAllInBatch();

            conversationService.removeParticipant(conversationId, 1L, 3L);

            ParticipantRemovedPayload payload = singlePayload(ParticipantRemovedPayload.class);
            assertThat(payload.userId()).isEqualTo(3L);
            assertThat(payload.selfLeave()).isFalse();
            assertThat(payload.recipients()).contains(3L);
        }

        /** Same trap on the self-service path. */
        @Test
        void leavingNotifiesTheLeaver() {
            outboxRepository.deleteAllInBatch();

            conversationService.leave(conversationId, 2L);

            ParticipantRemovedPayload payload = singlePayload(ParticipantRemovedPayload.class);
            assertThat(payload.userId()).isEqualTo(2L);
            assertThat(payload.selfLeave()).isTrue();
            assertThat(payload.recipients()).contains(2L);
        }

        /** Deletion cascades participants away, so recipients must be captured first. */
        @Test
        void deletionNotifiesEveryoneBeforeCascade() {
            outboxRepository.deleteAllInBatch();

            conversationService.delete(conversationId, 1L);

            ConversationDeletedPayload payload = singlePayload(ConversationDeletedPayload.class);
            assertThat(payload.recipients()).containsExactlyInAnyOrder(1L, 2L);
            assertThat(participantRepository.findUserIds(conversationId)).isEmpty();
        }

        @Test
        void addingAnExistingParticipantIsSilentlyIdempotent() {
            conversationService.addParticipant(conversationId, 1L, 3L);
            outboxRepository.deleteAllInBatch();

            conversationService.addParticipant(conversationId, 1L, 3L);

            assertThat(outboxRepository.findAll()).isEmpty();
            assertThat(participantRepository.findUserIds(conversationId)).hasSize(3);
        }
    }

}