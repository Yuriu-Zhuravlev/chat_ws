package com.yurii.zhuravlov.chatservice.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.data.domain.Persistable;

import java.time.Instant;

@Entity
@Table(name = "conversation_participants", schema = "chat_schema")
@Getter
public class ConversationParticipant implements Persistable<ParticipantId> {

    @EmbeddedId
    private ParticipantId id;

    @Setter
    private Long lastReadMessageId;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant joinedAt;

    @Transient
    private boolean isNew;

    public ConversationParticipant(Long conversationId, Long userId) {
        this.id = new ParticipantId(conversationId, userId);
        this.isNew = true;
    }

    protected ConversationParticipant() {
    }

    @Override
    public ParticipantId getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }

    public Long getConversationId() {
        return id.getConversationId();
    }

    public Long getUserId() {
        return id.getUserId();
    }
}