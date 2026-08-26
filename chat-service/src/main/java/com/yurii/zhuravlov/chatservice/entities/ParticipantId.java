package com.yurii.zhuravlov.chatservice.entities;

import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.io.Serializable;

@Embeddable
@Getter
@EqualsAndHashCode
public class ParticipantId implements Serializable {

    private Long conversationId;
    private Long userId;

    public ParticipantId(Long conversationId, Long userId) {
        this.conversationId = conversationId;
        this.userId = userId;
    }

    protected ParticipantId() {
    }
}