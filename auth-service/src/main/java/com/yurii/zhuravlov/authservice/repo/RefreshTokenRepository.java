package com.yurii.zhuravlov.authservice.repo;

import com.yurii.zhuravlov.authservice.entities.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Query("SELECT t FROM RefreshToken t JOIN FETCH t.user WHERE t.tokenHash = :hash")
    Optional<RefreshToken> findByTokenHashWithUser(@Param("hash") String hash);

    @Modifying
    @Query("UPDATE RefreshToken t SET t.revokedAt = :now " +
            "WHERE t.user.id = :userId AND t.usedAt IS NULL AND t.revokedAt IS NULL")
    int revokeActiveTokens(@Param("userId") Long userId, @Param("now") Instant now);

    @Modifying
    @Query("UPDATE RefreshToken t SET t.usedAt = :now " +
            "WHERE t.tokenHash = :hash AND t.usedAt IS NULL " +
            "AND t.revokedAt IS NULL AND t.expiresAt > :now")
    int markUsed(@Param("hash") String hash, @Param("now") Instant now);

    @Modifying
    @Query("UPDATE RefreshToken t SET t.revokedAt = :now " +
            "WHERE t.tokenHash = :hash AND t.usedAt IS NULL AND t.revokedAt IS NULL")
    int revokeByTokenHash(@Param("hash") String hash, @Param("now") Instant now);

    Optional<RefreshToken> findByUserIdAndUsedAtIsNullAndRevokedAtIsNull(Long userId);
}
