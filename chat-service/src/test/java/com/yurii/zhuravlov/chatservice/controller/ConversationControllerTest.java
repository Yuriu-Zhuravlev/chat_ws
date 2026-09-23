package com.yurii.zhuravlov.chatservice.controller;

import com.yurii.zhuravlov.chatservice.dto.request.CreateConversationRequest;
import com.yurii.zhuravlov.chatservice.dto.request.MarkReadRequest;
import com.yurii.zhuravlov.chatservice.dto.request.MessagePageRequest;
import com.yurii.zhuravlov.chatservice.dto.request.SendMessageRequest;
import com.yurii.zhuravlov.chatservice.dto.response.ConversationResponse;
import com.yurii.zhuravlov.chatservice.dto.response.MessagePageResponse;
import com.yurii.zhuravlov.chatservice.dto.response.MessageResponse;
import com.yurii.zhuravlov.chatservice.dto.response.SendResult;
import com.yurii.zhuravlov.chatservice.exceptions.ChatServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


class ConversationControllerTest extends WebMvcTestBase {

    private static final String BASE = "/api/conversations";

    @Nested
    class Security {

        static Stream<Arguments> endpoints() {
            return Stream.of(
                    Arguments.of(HttpMethod.GET, BASE),
                    Arguments.of(HttpMethod.POST, BASE),
                    Arguments.of(HttpMethod.GET, BASE + "/1"),
                    Arguments.of(HttpMethod.PATCH, BASE + "/1"),
                    Arguments.of(HttpMethod.DELETE, BASE + "/1"),
                    Arguments.of(HttpMethod.POST, BASE + "/1/participants"),
                    Arguments.of(HttpMethod.DELETE, BASE + "/1/participants/me"),
                    Arguments.of(HttpMethod.DELETE, BASE + "/1/participants/2"),
                    Arguments.of(HttpMethod.PUT, BASE + "/1/admin"),
                    Arguments.of(HttpMethod.GET, BASE + "/1/messages"),
                    Arguments.of(HttpMethod.POST, BASE + "/1/messages"),
                    Arguments.of(HttpMethod.POST, BASE + "/1/read"));
        }

        @ParameterizedTest(name = "{0} {1}")
        @MethodSource("endpoints")
        void everyEndpointRequiresAuthentication(HttpMethod method, String uri) throws Exception {
            mockMvc.perform(request(method, uri).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(conversationService);
        }

        /** The user id must come from the token, never from the request. */
        @Test
        void passesSubjectAsCurrentUser() throws Exception {
            mockMvc.perform(delete(BASE + "/7/participants/me").with(asUser(42)))
                    .andExpect(status().isNoContent());

            verify(conversationService).leave(7L, 42L);
        }
    }

    @Nested
    class Create {

        @Test
        void returnsCreatedWithLocation() throws Exception {
            when(conversationService.create(eq(1L), any())).thenReturn(conversation(10L));

            mockMvc.perform(post(BASE).with(asUser(1))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new CreateConversationRequest("chat", Set.of(2L)))))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", BASE + "/10"))
                    .andExpect(jsonPath("$.id").value(10));
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   "})
        void rejectsBlankTitle(String title) throws Exception {
            postCreate("{\"title\":\"%s\",\"participantIds\":[2]}".formatted(title))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void rejectsTooLongTitle() throws Exception {
            postCreate("{\"title\":\"%s\",\"participantIds\":[2]}".formatted("x".repeat(129)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void rejectsTooManyParticipants() throws Exception {
            Set<Long> ids = LongStream.rangeClosed(2, 101).boxed().collect(Collectors.toSet());

            postCreate(json(new CreateConversationRequest("chat", ids)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void rejectsNullParticipantId() throws Exception {
            postCreate("{\"title\":\"chat\",\"participantIds\":[2,null]}")
                    .andExpect(status().isBadRequest());
        }

        @Test
        void rejectsMalformedJson() throws Exception {
            postCreate("{\"title\":").andExpect(status().isBadRequest());
        }

        private ResultActions postCreate(String body) throws Exception {
            ResultActions result = mockMvc.perform(post(BASE).with(asUser(1))
                    .contentType(MediaType.APPLICATION_JSON).content(body));
            verifyNoInteractions(conversationService);
            return result;
        }
    }

    @Nested
    class Participants {

        @Test
        void rejectsMissingUserId() throws Exception {
            mockMvc.perform(post(BASE + "/1/participants").with(asUser(1))
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(conversationService);
        }

        /** The literal segment must win over the path variable. */
        @Test
        void meSegmentRoutesToLeave() throws Exception {
            mockMvc.perform(delete(BASE + "/1/participants/me").with(asUser(5)))
                    .andExpect(status().isNoContent());

            verify(conversationService).leave(1L, 5L);
            verify(conversationService, never()).removeParticipant(any(), any(), any());
        }

        @Test
        void rejectsNonNumericParticipantId() throws Exception {
            mockMvc.perform(delete(BASE + "/1/participants/abc").with(asUser(1)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    class Messages {

        private static final String CONTENT = "hello";
        private static final String CLIENT_ID = "c1";

        @Nested
        class Send {

            /** Baseline for every negative case below: this exact body is accepted. */
            @Test
            void returnsCreatedForNewMessage() throws Exception {
                when(conversationService.send(eq(1L), eq(1L), any())).thenReturn(sendResult(true));

                postMessage(new SendMessageRequest(CONTENT, CLIENT_ID))
                        .andExpect(status().isCreated());

                verify(conversationService).send(1L, 1L, new SendMessageRequest(CONTENT, CLIENT_ID));
            }

            /** A retry is not a new resource: 200 with the stored message. */
            @Test
            void returnsOkForIdempotentRetry() throws Exception {
                when(conversationService.send(eq(1L), eq(1L), any())).thenReturn(sendResult(false));

                postMessage(new SendMessageRequest(CONTENT, CLIENT_ID))
                        .andExpect(status().isOk());
            }

            @Test
            void acceptsContentAtTheLimit() throws Exception {
                when(conversationService.send(any(), any(), any())).thenReturn(sendResult(true));

                postMessage(new SendMessageRequest("x".repeat(4000), CLIENT_ID))
                        .andExpect(status().isCreated());
            }

            @Test
            void acceptsClientIdAtTheLimit() throws Exception {
                when(conversationService.send(any(), any(), any())).thenReturn(sendResult(true));

                postMessage(new SendMessageRequest(CONTENT, "k".repeat(64)))
                        .andExpect(status().isCreated());
            }

            /** Each case breaks exactly one field of the accepted baseline. */
            static Stream<Arguments> invalidRequests() {
                return Stream.of(
                        Arguments.of("content missing", new SendMessageRequest(null, CLIENT_ID)),
                        Arguments.of("content empty", new SendMessageRequest("", CLIENT_ID)),
                        Arguments.of("content blank", new SendMessageRequest("   ", CLIENT_ID)),
                        Arguments.of("content too long", new SendMessageRequest("x".repeat(4001), CLIENT_ID)),
                        Arguments.of("client id missing", new SendMessageRequest(CONTENT, null)),
                        Arguments.of("client id blank", new SendMessageRequest(CONTENT, "   ")),
                        Arguments.of("client id too long", new SendMessageRequest(CONTENT, "k".repeat(65))));
            }

            @ParameterizedTest(name = "{0}")
            @MethodSource("invalidRequests")
            void rejectsInvalidRequest(String description, SendMessageRequest request) throws Exception {
                postMessage(request).andExpect(status().isBadRequest());

                verifyNoInteractions(conversationService);
            }

            @Test
            void rejectsMalformedJson() throws Exception {
                mockMvc.perform(post(BASE + "/1/messages").with(asUser(1))
                                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":"))
                        .andExpect(status().isBadRequest());

                verifyNoInteractions(conversationService);
            }

            private ResultActions postMessage(SendMessageRequest request) throws Exception {
                return mockMvc.perform(post(BASE + "/1/messages").with(asUser(1))
                        .contentType(MediaType.APPLICATION_JSON).content(json(request)));
            }
        }

        @Nested
        class History {

            @BeforeEach
            void emptyPage() {
                when(conversationService.messages(any(), any(), any()))
                        .thenReturn(new MessagePageResponse(List.of(), false));
            }

            @Test
            void bindsPagingParameters() throws Exception {
                mockMvc.perform(get(BASE + "/1/messages").with(asUser(1))
                                .param("messageId", "50").param("isBefore", "false").param("limit", "10"))
                        .andExpect(status().isOk());

                verify(conversationService).messages(1L, 1L, new MessagePageRequest(50L, false, 10));
            }

            /** Opening a chat sends nothing: newest page, backwards, default limit. */
            @Test
            void defaultsToNewestPage() throws Exception {
                mockMvc.perform(get(BASE + "/1/messages").with(asUser(1)))
                        .andExpect(status().isOk());

                verify(conversationService).messages(1L, 1L, new MessagePageRequest(null, true, 50));
            }

            /** Oversized limit is a clear intent ("give me more"), so it is capped, not rejected. */
            @Test
            void capsLimit() throws Exception {
                mockMvc.perform(get(BASE + "/1/messages").with(asUser(1)).param("limit", "500"))
                        .andExpect(status().isOk());

                verify(conversationService).messages(1L, 1L, new MessagePageRequest(null, true, 100));
            }

            @Test
            void rejectsNonNumericCursor() throws Exception {
                mockMvc.perform(get(BASE + "/1/messages").with(asUser(1)).param("messageId", "abc"))
                        .andExpect(status().isBadRequest());

                verifyNoInteractions(conversationService);
            }
        }

        @Nested
        class Read {

            /** Baseline for the negative case below. */
            @Test
            void returnsNoContent() throws Exception {
                mockMvc.perform(post(BASE + "/1/read").with(asUser(1))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(new MarkReadRequest(5L))))
                        .andExpect(status().isNoContent());

                verify(conversationService).markRead(1L, 1L, 5L);
            }

            @Test
            void rejectsMissingMessageId() throws Exception {
                mockMvc.perform(post(BASE + "/1/read").with(asUser(1))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(new MarkReadRequest(null))))
                        .andExpect(status().isBadRequest());

                verifyNoInteractions(conversationService);
            }
        }
    }

    @Nested
    class ErrorMapping {

        /** The service decides the status; the handler must carry it through unchanged. */
        @Test
        void mapsServiceExceptionToItsStatus() throws Exception {
            when(conversationService.details(1L, 1L)).thenThrow(
                    new ChatServiceException("Conversation 1 not found", HttpStatus.NOT_FOUND));

            mockMvc.perform(get(BASE + "/1").with(asUser(1)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.path").value(BASE + "/1"));
        }
    }

    private static ConversationResponse conversation(long id) {
        return new ConversationResponse(id, "chat", 1L, List.of(), Instant.now(), null);
    }

    private static SendResult sendResult(boolean created) {
        return new SendResult(
                new MessageResponse(1L, 1L, 1L, "c1", "hello", Instant.now()), created);
    }
}