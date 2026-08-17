package com.yurii.zhuravlov.authservice.entities;

import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "users", schema = "auth_schema")
@Getter
public class User {
    @Id
    @GeneratedValue
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String passwordHash;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public User(String passwordHashed, String username) {
        this.passwordHash = passwordHashed;
        this.username = username;
    }

    protected User() {
    }
}
