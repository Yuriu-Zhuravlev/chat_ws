package com.yurii.zhuravlov.authservice.mapper;

import com.yurii.zhuravlov.authservice.config.properties.JwtProperties;
import com.yurii.zhuravlov.authservice.dto.TokenPair;
import com.yurii.zhuravlov.authservice.dto.responses.TokenResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TokenResponseMapper {

    private final JwtProperties properties;

    public TokenResponse toResponse(TokenPair pair) {
        return new TokenResponse(
                pair.accessToken(),
                pair.refreshToken(),
                "Bearer",
                properties.accessTokenTtl().toSeconds()
        );
    }
}