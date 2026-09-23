package com.yurii.zhuravlov.chatservice.controller;

import com.yurii.zhuravlov.chatservice.config.SecurityConfig;
import com.yurii.zhuravlov.chatservice.config.WebMvcConfig;
import com.yurii.zhuravlov.chatservice.security.CurrentUserIdArgumentResolver;
import com.yurii.zhuravlov.chatservice.service.ConversationService;
import com.yurii.zhuravlov.chatservice.service.UserSearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;


@WebMvcTest
@Import({SecurityConfig.class, CurrentUserIdArgumentResolver.class, WebMvcConfig.class})
public abstract class WebMvcTestBase {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired protected ObjectMapper objectMapper;

    @MockitoBean
    protected ConversationService conversationService;

    @MockitoBean
    protected UserSearchService userSearchService;

    /** SecurityConfig wires a resource server; the slice does not load JwtDecoderConfig. */
    @MockitoBean protected JwtDecoder jwtDecoder;

    protected static RequestPostProcessor asUser(long id) {
        return jwt().jwt(j -> j.subject(String.valueOf(id)));
    }

    protected String json(Object body) {
        return objectMapper.writeValueAsString(body);
    }
}