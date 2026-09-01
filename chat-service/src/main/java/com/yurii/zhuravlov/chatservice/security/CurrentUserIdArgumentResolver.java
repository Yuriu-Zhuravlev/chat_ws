package com.yurii.zhuravlov.chatservice.security;

import org.jspecify.annotations.NonNull;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.Objects;

@Component
public class CurrentUserIdArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUserId.class)
                && Long.class.equals(parameter.getParameterType());
    }

    @Override
    public Long resolveArgument(@NonNull MethodParameter parameter,
                                ModelAndViewContainer mavContainer,
                                @NonNull NativeWebRequest webRequest,
                                WebDataBinderFactory binderFactory) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new IllegalStateException(
                    "@CurrentUserId used on an endpoint without JWT authentication");
        }

        String subject = jwt.getSubject();
        try {
            return Long.valueOf(Objects.requireNonNull(subject));
        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                    "JWT subject is not a numeric user id: " + subject, e);
        }
    }
}