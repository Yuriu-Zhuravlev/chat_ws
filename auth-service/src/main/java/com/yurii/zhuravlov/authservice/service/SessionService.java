package com.yurii.zhuravlov.authservice.service;

import com.yurii.zhuravlov.authservice.dto.TokenPair;
import com.yurii.zhuravlov.authservice.entities.RefreshToken;
import com.yurii.zhuravlov.authservice.entities.User;
import com.yurii.zhuravlov.authservice.exceptions.RefreshTokenException;
import com.yurii.zhuravlov.authservice.exceptions.TokenTheftException;
import com.yurii.zhuravlov.authservice.repo.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class SessionService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenIssuer tokenIssuer;

    private static final Duration GRACE = Duration.ofSeconds(30);


    @Transactional
    public TokenPair startNewSession(User user) {
        Instant now = Instant.now();
        refreshTokenRepository.revokeActiveTokens(user.getId(), now);
        return tokenIssuer.issue(user, now);
    }

    @Transactional(noRollbackFor = {RefreshTokenException.class, TokenTheftException.class})
    public TokenPair refreshSession(String tokenHash) {
        Instant now = Instant.now();

        if (refreshTokenRepository.markUsed(tokenHash, now) == 1) {
            RefreshToken token = refreshTokenRepository.findByTokenHashWithUser(tokenHash)
                    .orElseThrow(() -> new IllegalStateException("Token vanished"));
            return tokenIssuer.issue(token.getUser(), now);
        }

        RefreshToken refreshToken = refreshTokenRepository.findByTokenHashWithUser(tokenHash)
                .orElseThrow(() -> new RefreshTokenException("Invalid token"));

        if (refreshToken.getUsedAt() != null
                && Duration.between(refreshToken.getUsedAt(), now).compareTo(GRACE) <= 0) {
            throw new RefreshTokenException("Token already used, retry");
        }

        if (refreshToken.getUsedAt() != null){
            Long userId = refreshToken.getUser().getId();
            log.warn("Refresh token reuse detected: userId={}, tokenId={}, usedAt={}, attemptAt={}",
                    userId, refreshToken.getId(), refreshToken.getUsedAt(), now);
            refreshTokenRepository.revokeActiveTokens(userId, now);
            throw new TokenTheftException("Suspicious action found, session terminated");
        }

        throw new RefreshTokenException("Invalid token");
    }

    @Transactional
    public void sessionLogout(String tokenHash){
        refreshTokenRepository.revokeByTokenHash(tokenHash, Instant.now());
    }
}