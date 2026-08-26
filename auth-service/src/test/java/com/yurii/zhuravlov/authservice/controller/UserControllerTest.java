package com.yurii.zhuravlov.authservice.controller;

import com.yurii.zhuravlov.authservice.config.SecurityConfig;
import com.yurii.zhuravlov.authservice.dto.responses.UserResponse;
import com.yurii.zhuravlov.authservice.handlers.GlobalExceptionHandler;
import com.yurii.zhuravlov.authservice.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@WebMvcTest(controllers = {UserController.class})
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class UserControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean UserService userService;
    @MockitoBean JwtDecoder jwtDecoder;

    @Test
    void meShouldRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userService);
    }

    @Test
    void meShouldParseSubjectAsUserId() throws Exception {
        when(userService.getById(42L)).thenReturn(new UserResponse(42L, "vasyl"));

        mockMvc.perform(get("/api/users/me")
                        .with(jwt().jwt(b -> b.subject("42"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.username").value("vasyl"));

        verify(userService).getById(42L);
    }

}