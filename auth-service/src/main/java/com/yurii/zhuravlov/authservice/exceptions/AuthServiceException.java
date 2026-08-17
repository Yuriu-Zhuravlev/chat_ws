package com.yurii.zhuravlov.authservice.exceptions;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class AuthServiceException extends RuntimeException {
    private final HttpStatus httpStatus;
    public AuthServiceException(String message, HttpStatus httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }
}
