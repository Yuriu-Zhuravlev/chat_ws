package com.yurii.zhuravlov.authservice.exceptions;

import org.springframework.http.HttpStatus;

public class UserAlreadyExists extends AuthServiceException {
    public UserAlreadyExists(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
