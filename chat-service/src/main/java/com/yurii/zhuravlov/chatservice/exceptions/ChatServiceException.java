package com.yurii.zhuravlov.chatservice.exceptions;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ChatServiceException extends RuntimeException {
    private final HttpStatus httpStatus;
    public ChatServiceException(String message, HttpStatus httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }
}
