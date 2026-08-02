package com.yurii.zhuravlov.authservice.entities;

import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "USERS", schema = "AUTH_SCHEMA")
@Getter
public class User {
    @Id
    @GeneratedValue
    UUID id;

    @Column(unique = true, nullable = false)
    String username;

    @Column(nullable = false)
    String passwordHashed;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    LocalDateTime createdAt;

    public User(String passwordHashed, String username) {
        this.passwordHashed = passwordHashed;
        this.username = username;
    }

    protected User() {
    }
}
