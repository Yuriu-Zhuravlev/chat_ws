package com.yurii.zhuravlov.authservice.exceptions;

import org.springframework.http.HttpStatus;

public class TokenTheftException extends AuthServiceException {
    public TokenTheftException(String message) {
        super(message, HttpStatus.UNAUTHORIZED);
    }
}