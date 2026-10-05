package com.yurii.zhuravlov.notificationservice.ws;

import com.yurii.zhuravlov.contracts.chat.*;
import com.yurii.zhuravlov.notificationservice.ws.frame.ClientEvent;
import com.yurii.zhuravlov.notificationservice.ws.frame.EventPayloads;
import org.springframework.stereotype.Component;

@Component
public class ChatEventMapper {

    /**
     * Pattern-matching switch rather than a registry of per-type mappers: dispatch is on
     * the generated class itself, so the compiler checks it and no string keys exist to
     * drift. The wire "type" is derived from the same class, never written by hand.
     */
    public ClientEvent toClientEvent(ChatEvent event) {
        Object payload = event.getPayload();

        Object mapped = switch (payload) {
            case MessageCreated p -> new EventPayloads.MessageCreated(
                    p.getMessageId(), p.getSenderId(), p.getClientMessageId(),
                    p.getContent(), p.getCreatedAt());

            case MessagesRead p -> new EventPayloads.MessagesRead(
                    p.getReaderId(), p.getLastReadMessageId());

            case ParticipantAdded p -> new EventPayloads.ParticipantAdded(
                    p.getUserId(), p.getUsername());

            case ParticipantRemoved p -> new EventPayloads.ParticipantRemoved(
                    p.getUserId(), p.getSelfLeave());

            case ConversationUpdated p -> new EventPayloads.ConversationUpdated(
                    p.getTitle(), p.getAdminId());

            case ConversationDeleted ignored -> new EventPayloads.ConversationDeleted();

            case ConversationCreated p -> new EventPayloads.ConversationCreated(
                    p.getTitle(), p.getAdminId(),
                    p.getParticipants().stream()
                            .map(i -> new EventPayloads.Participant(i.getUserId(), i.getUsername()))
                            .toList());

            default -> throw new IllegalStateException(
                    "Unmapped payload type: " + payload.getClass().getName());
        };

        return new ClientEvent(
                event.getEventId(),
                event.getConversationId(),
                event.getOccurredAt(),
                payload.getClass().getSimpleName(),
                mapped);
    }
}