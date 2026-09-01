package com.yurii.zhuravlov.chatservice.handlers;

import com.yurii.zhuravlov.chatservice.dto.errors.ErrorResponse;
import com.yurii.zhuravlov.chatservice.exceptions.ChatServiceException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ChatServiceException.class)
    public ResponseEntity<ErrorResponse> handleAuthServiceException(ChatServiceException e, HttpServletRequest request) {
        ErrorResponse error = new ErrorResponse(
                "Chat service: exception occurred",
                e.getMessage(),
                request.getRequestURI(),
                e.getHttpStatus().value()
        );

        return new ResponseEntity<>(error, e.getHttpStatus());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request) {

        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining(", "));

        ErrorResponse error = new ErrorResponse(
                "Validation Failed",
                details,
                path(request),
                HttpStatus.BAD_REQUEST.value()
        );

        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    private String path(WebRequest request) {
        return ((ServletWebRequest) request).getRequest().getRequestURI();
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, @NonNull HttpHeaders headers,
            HttpStatusCode statusCode, @NonNull WebRequest request) {

        ErrorResponse error = new ErrorResponse(
                HttpStatus.valueOf(statusCode.value()).getReasonPhrase(),
                ex.getMessage(),
                path(request),
                statusCode.value()
        );
        return new ResponseEntity<>(error, headers, statusCode);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {

        String details = ex.getConstraintViolations().stream()
                .map(v -> lastNode(v.getPropertyPath()) + ": " + v.getMessage())
                .collect(Collectors.joining(", "));

        ErrorResponse error = new ErrorResponse(
                "Validation Failed",
                details,
                request.getRequestURI(),
                HttpStatus.BAD_REQUEST.value()
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    private String lastNode(Path path) {
        String full = path.toString();          // "search.query"
        int dot = full.lastIndexOf('.');
        return dot >= 0 ? full.substring(dot + 1) : full;
    }
}
