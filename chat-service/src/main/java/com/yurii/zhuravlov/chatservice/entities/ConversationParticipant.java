package com.yurii.zhuravlov.chatservice.entities;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "conversation_participants", schema = "chat_schema")
@Getter
public class ConversationParticipant {

    @EmbeddedId
    private ParticipantId id;

    @Setter
    private Long lastReadMessageId;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant joinedAt;

    public ConversationParticipant(Long conversationId, Long userId) {
        this.id = new ParticipantId(conversationId, userId);
    }

    protected ConversationParticipant() {
    }

    public Long getConversationId() {
        return id.getConversationId();
    }

    public Long getUserId() {
        return id.getUserId();
    }
}