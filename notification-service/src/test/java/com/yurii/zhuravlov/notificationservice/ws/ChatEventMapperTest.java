package com.yurii.zhuravlov.notificationservice.ws;

import com.yurii.zhuravlov.contracts.chat.*;
import com.yurii.zhuravlov.notificationservice.ws.frame.ClientEvent;
import com.yurii.zhuravlov.notificationservice.ws.frame.EventPayloads;
import org.apache.avro.Schema;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatEventMapperTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-09-24T08:47:39.314Z");

    private final ChatEventMapper mapper = new ChatEventMapper();

    @Test
    void carriesEnvelopeFieldsThrough() {
        ClientEvent event = mapper.toClientEvent(envelope(messageCreated()));

        assertThat(event.eventId()).isEqualTo(202L);
        assertThat(event.conversationId()).isEqualTo(4L);
        assertThat(event.occurredAt()).isEqualTo(OCCURRED_AT);
    }

    /** The wire type comes from the generated class, so it cannot drift from the schema. */
    @Test
    void namesTheTypeAfterThePayloadClass() {
        assertThat(mapper.toClientEvent(envelope(messageCreated())).type())
                .isEqualTo("MessageCreated");
    }

    @Test
    void mapsMessageCreated() {
        ClientEvent event = mapper.toClientEvent(envelope(messageCreated()));

        assertThat(event.payload())
                .isEqualTo(new EventPayloads.MessageCreated(
                        7L, 1L, "c1", "hello", OCCURRED_AT));
    }

    @Test
    void mapsMessagesRead() {
        ClientEvent event = mapper.toClientEvent(envelope(
                MessagesRead.newBuilder().setReaderId(1L).setLastReadMessageId(9L).build()));

        assertThat(event.payload()).isEqualTo(new EventPayloads.MessagesRead(1L, 9L));
        assertThat(event.type()).isEqualTo("MessagesRead");
    }

    @Test
    void mapsParticipantAdded() {
        ClientEvent event = mapper.toClientEvent(envelope(
                ParticipantAdded.newBuilder().setUserId(3L).setUsername("vasyl").build()));

        assertThat(event.payload()).isEqualTo(new EventPayloads.ParticipantAdded(3L, "vasyl"));
    }

    /** selfLeave distinguishes "left" from "was removed": different system messages in the UI. */
    @Test
    void mapsParticipantRemovedKeepingSelfLeave() {
        ClientEvent event = mapper.toClientEvent(envelope(
                ParticipantRemoved.newBuilder().setUserId(3L).setSelfLeave(true).build()));

        assertThat(event.payload()).isEqualTo(new EventPayloads.ParticipantRemoved(3L, true));
    }

    @Test
    void mapsConversationUpdated() {
        ClientEvent event = mapper.toClientEvent(envelope(
                ConversationUpdated.newBuilder().setTitle("renamed").setAdminId(2L).build()));

        assertThat(event.payload()).isEqualTo(new EventPayloads.ConversationUpdated("renamed", 2L));
    }

    @Test
    void mapsConversationDeleted() {
        ClientEvent event = mapper.toClientEvent(envelope(ConversationDeleted.newBuilder().build()));

        assertThat(event.payload()).isEqualTo(new EventPayloads.ConversationDeleted());
        assertThat(event.type()).isEqualTo("ConversationDeleted");
    }

    @Test
    void mapsConversationCreatedWithRoster() {
        ClientEvent event = mapper.toClientEvent(envelope(ConversationCreated.newBuilder()
                .setTitle("chat")
                .setAdminId(1L)
                .setParticipants(List.of(
                        ParticipantInfo.newBuilder().setUserId(1L).setUsername("alice").build(),
                        ParticipantInfo.newBuilder().setUserId(2L).setUsername("bob").build()))
                .build()));

        assertThat(event.payload()).isEqualTo(new EventPayloads.ConversationCreated(
                "chat", 1L, List.of(
                        new EventPayloads.Participant(1L, "alice"),
                        new EventPayloads.Participant(2L, "bob"))));
    }

    /**
     * A union branch added to the schema without a case here must fail loudly. Silently
     * forwarding an unmapped payload would put the Avro object itself into the frame,
     * schema and all.
     */
    @Test
    void rejectsUnmappedPayload() {
        ChatEvent event = new ChatEvent();
        event.setPayload("not a payload");

        assertThatThrownBy(() -> mapper.toClientEvent(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unmapped payload type");
    }

    /**
     * Canary: the mapper's switch is exhaustive only by convention, so this fails when the
     * schema grows a branch and points whoever added it at ChatEventMapper.
     */
    @Test
    void coversEveryUnionBranch() {
        List<Schema> branches = ChatEvent.getClassSchema().getField("payload").schema().getTypes();

        assertThat(branches).extracting(Schema::getName).containsExactlyInAnyOrder(
                "MessageCreated", "MessagesRead", "ParticipantAdded", "ParticipantRemoved",
                "ConversationUpdated", "ConversationDeleted", "ConversationCreated");
    }

    private static MessageCreated messageCreated() {
        return MessageCreated.newBuilder()
                .setMessageId(7L)
                .setSenderId(1L)
                .setClientMessageId("c1")
                .setContent("hello")
                .setCreatedAt(OCCURRED_AT)
                .build();
    }

    private static ChatEvent envelope(Object payload) {
        return ChatEvent.newBuilder()
                .setEventId(202L)
                .setConversationId(4L)
                .setOccurredAt(OCCURRED_AT)
                .setRecipients(List.of(1L, 2L))
                .setPayload(payload)
                .build();
    }
}