package com.yurii.zhuravlov.chatservice.controller;

import com.yurii.zhuravlov.chatservice.config.SecurityConfig;
import com.yurii.zhuravlov.chatservice.config.WebMvcConfig;
import com.yurii.zhuravlov.chatservice.security.CurrentUserIdArgumentResolver;
import com.yurii.zhuravlov.chatservice.service.UserSearchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, CurrentUserIdArgumentResolver.class, WebMvcConfig.class})
class UserControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    UserSearchService userSearchService;

    /** SecurityConfig wires a resource server; without a decoder bean the slice fails to start. */
    @MockitoBean
    JwtDecoder jwtDecoder;

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/users/search").param("query", "vasyl"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userSearchService);
    }

    @Test
    void passesSubjectFromTokenAsRequesterId() throws Exception {
        when(userSearchService.search(any(), any(), anyInt())).thenReturn(List.of());

        mockMvc.perform(get("/api/users/search")
                        .param("query", "vasyl")
                        .with(jwt().jwt(j -> j.subject("42"))))
                .andExpect(status().isOk());

        verify(userSearchService).search("vasyl", 42L, 0);
    }

    @Test
    void defaultsPageToZeroWhenAbsent() throws Exception {
        when(userSearchService.search(any(), any(), anyInt())).thenReturn(List.of());

        mockMvc.perform(get("/api/users/search")
                        .param("query", "vasyl")
                        .with(jwt().jwt(j -> j.subject("1"))))
                .andExpect(status().isOk());

        verify(userSearchService).search(eq("vasyl"), any(), eq(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "a"})
    void rejectsInvalidQuery(String query) throws Exception {
        mockMvc.perform(get("/api/users/search")
                        .param("query", query)
                        .with(jwt().jwt(j -> j.subject("1"))))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userSearchService);
    }

    @Test
    void rejectsMissingQuery() throws Exception {
        mockMvc.perform(get("/api/users/search").with(jwt().jwt(j -> j.subject("1"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsNegativePage() throws Exception {
        mockMvc.perform(get("/api/users/search")
                        .param("query", "vasyl")
                        .param("page", "-1")
                        .with(jwt().jwt(j -> j.subject("1"))))
                .andExpect(status().isBadRequest());
    }
}