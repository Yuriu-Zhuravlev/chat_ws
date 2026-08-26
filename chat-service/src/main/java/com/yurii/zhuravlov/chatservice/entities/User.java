package com.yurii.zhuravlov.chatservice.entities;

import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "users", schema = "chat_schema")
@Getter
public class User {

    @Id
    private Long id;                    // приходить із UserRegistered, не генерується

    @Column(nullable = false)
    private String username;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public User(Long id, String username) {
        this.id = id;
        this.username = username;
    }

    protected User() {
    }
}