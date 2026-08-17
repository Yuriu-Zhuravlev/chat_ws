package com.yurii.zhuravlov.authservice.exceptions;

import org.springframework.http.HttpStatus;

public class RefreshTokenException extends AuthServiceException {
    public RefreshTokenException(String message) {
        super(message, HttpStatus.UNAUTHORIZED);
    }
}
