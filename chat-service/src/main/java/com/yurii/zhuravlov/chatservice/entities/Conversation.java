package com.yurii.zhuravlov.chatservice.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "conversations", schema = "chat_schema")
@Getter
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @Column(nullable = false)
    private String title;

    @Setter
    @Column(nullable = false)
    private Long adminId;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Setter
    private Instant lastMessageAt;

    public Conversation(String title, Long adminId) {
        this.title = title;
        this.adminId = adminId;
    }

    protected Conversation() {
    }
}