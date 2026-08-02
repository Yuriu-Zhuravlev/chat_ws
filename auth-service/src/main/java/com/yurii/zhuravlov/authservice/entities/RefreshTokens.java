package com.yurii.zhuravlov.authservice.entities;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "REFRESH_TOKENS", schema = "AUTH_SCHEMA")
@Getter
public class RefreshTokens {
    @Id
    @GeneratedValue
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    User user;

    @Column(unique = true, nullable = false)
    String tokenHash;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    LocalDateTime createdAt;

    @Column(nullable = false)
    LocalDateTime expiredAt;

    @Setter
    LocalDateTime usedAt;

    @Setter
    LocalDateTime revokedAt;

    @Builder
    public RefreshTokens(User user, String tokenHash, LocalDateTime expiredAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.expiredAt = expiredAt;
    }

    protected RefreshTokens() {
    }
}
