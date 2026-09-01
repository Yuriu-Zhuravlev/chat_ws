package com.yurii.zhuravlov.authservice.controller;

import com.yurii.zhuravlov.authservice.config.SecurityConfig;
import com.yurii.zhuravlov.authservice.dto.TokenPair;
import com.yurii.zhuravlov.authservice.dto.responses.TokenResponse;
import com.yurii.zhuravlov.authservice.dto.responses.UserResponse;
import com.yurii.zhuravlov.authservice.exceptions.TokenTheftException;
import com.yurii.zhuravlov.authservice.exceptions.UserAlreadyExists;
import com.yurii.zhuravlov.authservice.handlers.GlobalExceptionHandler;
import com.yurii.zhuravlov.authservice.mapper.TokenResponseMapper;
import com.yurii.zhuravlov.authservice.service.AuthService;
import com.yurii.zhuravlov.authservice.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class AuthControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AuthService authService;
    @MockitoBean
    UserService userService;
    @MockitoBean
    TokenResponseMapper tokenResponseMapper;
    @MockitoBean
    JwtDecoder jwtDecoder;

    @Test
    void registerShouldReturn201() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"username":"vasyl","password":"password123"}"""))
                .andExpect(status().isCreated());

        verify(userService).register(any());
    }

    @Test
    void registerShouldReturn409WhenUsernameTaken() throws Exception {
        doThrow(new UserAlreadyExists("Username is already taken"))
                .when(userService).register(any());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"username":"vasyl","password":"password123"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void registerShouldRejectShortPasswordBeforeReachingService() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content("""
                        {"username":"vasyl","password":"short"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"));

        verifyNoInteractions(userService);
    }

    @Test
    void registerShouldRejectInvalidUsernameCharacters() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"username":"vasyl petrenko!","password":"password123"}"""))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userService);
    }

    @Test
    void loginShouldReturnFullTokenResponse() throws Exception {
        when(authService.login(any())).thenReturn(new TokenPair("jwt-value", "refresh-value"));
        when(tokenResponseMapper.toResponse(any()))
                .thenReturn(new TokenResponse("jwt-value", "refresh-value", "Bearer", 900L));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"username":"vasyl","password":"password123"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("jwt-value"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-value"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900));
    }

    @Test
    void loginShouldReturn401OnBadCredentials() throws Exception {
        when(authService.login(any()))
                .thenThrow(new BadCredentialsException("Invalid username or password"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"username":"vasyl","password":"wrong"}"""))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginShouldNotApplyRegistrationPasswordRules() throws Exception {
        when(authService.login(any())).thenReturn(new TokenPair("jwt", "refresh"));
        when(tokenResponseMapper.toResponse(any()))
                .thenReturn(new TokenResponse("jwt", "refresh", "Bearer", 900L));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"username":"vasyl","password":"old"}"""))
                .andExpect(status().isOk());
    }

    @Test
    void refreshShouldReturn401OnTokenTheft() throws Exception {
        when(authService.refresh(anyString()))
                .thenThrow(new TokenTheftException("Session terminated"));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"refreshToken":"stolen-token"}"""))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutShouldReturn204() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"refreshToken":"some-token"}"""))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldReturn400OnMalformedJson() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn405OnWrongHttpMethod() throws Exception {
        mockMvc.perform(get("/api/auth/login"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void meShouldRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userService);
    }

    @Test
    void meShouldParseSubjectAsUserId() throws Exception {
        when(userService.getById(42L)).thenReturn(new UserResponse(42L, "vasyl"));

        mockMvc.perform(get("/api/auth/me")
                        .with(jwt().jwt(b -> b.subject("42"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.username").value("vasyl"));

        verify(userService).getById(42L);
    }
}