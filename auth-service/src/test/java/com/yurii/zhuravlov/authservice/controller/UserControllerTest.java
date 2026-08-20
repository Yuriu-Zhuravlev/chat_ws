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

import java.util.List;

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
    void searchShouldRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/users/search").param("query", "vasyl"))
                .andExpect(status().isUnauthorized());
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

    @Test
    void searchShouldRejectSingleCharacterQuery() throws Exception {
        mockMvc.perform(get("/api/users/search")
                        .param("query", "a")
                        .with(jwt().jwt(b -> b.subject("1"))))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userService);
    }

    @Test
    void searchShouldRejectMissingQuery() throws Exception {
        mockMvc.perform(get("/api/users/search")
                        .with(jwt().jwt(b -> b.subject("1"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void searchShouldPassRequesterIdForExclusion() throws Exception {
        when(userService.search(eq("vas"), eq(7L), anyInt()))
                .thenReturn(List.of(new UserResponse(9L, "vasylina")));

        mockMvc.perform(get("/api/users/search")
                        .param("query", "vas")
                        .with(jwt().jwt(b -> b.subject("7"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("vasylina"));

        verify(userService).search("vas", 7L, 0);
    }

    @Test
    void searchShouldDefaultToFirstPage() throws Exception {
        when(userService.search(anyString(), anyLong(), anyInt())).thenReturn(List.of());

        mockMvc.perform(get("/api/users/search")
                        .param("query", "vas")
                        .with(jwt().jwt(b -> b.subject("1"))))
                .andExpect(status().isOk());

        verify(userService).search("vas", 1L, 0);
    }
}