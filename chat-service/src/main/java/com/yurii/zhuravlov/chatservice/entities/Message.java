package com.yurii.zhuravlov.chatservice.entities;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "messages", schema = "chat_schema")
@Getter
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long conversationId;

    @Column(nullable = false, updatable = false)
    private Long senderId;

    @Column(nullable = false, updatable = false)
    private String clientMessageId;

    @Column(nullable = false)
    private String content;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Builder
    public Message(Long conversationId, Long senderId, String clientMessageId, String content) {
        this.conversationId = conversationId;
        this.senderId = senderId;
        this.clientMessageId = clientMessageId;
        this.content = content;
    }

    protected Message() {
    }
}