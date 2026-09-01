package com.yurii.zhuravlov.chatservice.service;

import com.yurii.zhuravlov.chatservice.dto.projection.ConversationSummaryRow;
import com.yurii.zhuravlov.chatservice.dto.projection.ParticipantRow;
import com.yurii.zhuravlov.chatservice.dto.request.CreateConversationRequest;
import com.yurii.zhuravlov.chatservice.dto.response.ConversationResponse;
import com.yurii.zhuravlov.chatservice.dto.response.ConversationSummaryResponse;
import com.yurii.zhuravlov.chatservice.dto.response.UserResponse;
import com.yurii.zhuravlov.chatservice.entities.Conversation;
import com.yurii.zhuravlov.chatservice.entities.ConversationParticipant;
import com.yurii.zhuravlov.chatservice.entities.ParticipantId;
import com.yurii.zhuravlov.chatservice.entities.User;
import com.yurii.zhuravlov.chatservice.exceptions.ChatServiceException;
import com.yurii.zhuravlov.chatservice.outbox.OutboxRecorder;
import com.yurii.zhuravlov.chatservice.outbox.payload.*;
import com.yurii.zhuravlov.chatservice.repo.ConversationParticipantRepository;
import com.yurii.zhuravlov.chatservice.repo.ConversationRepository;
import com.yurii.zhuravlov.chatservice.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

import static com.yurii.zhuravlov.chatservice.constants.Constants.PAGE_SIZE;

@Service
@RequiredArgsConstructor
public class ConversationService {

    private static final int MAX_PARTICIPANTS = 100;
    private static final int PREVIEW_SIZE = 4;

    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final UserRepository userRepository;
    private final OutboxRecorder outboxRecorder;

    @Transactional
    public ConversationResponse create(Long me, CreateConversationRequest request) {
        Set<Long> members = new LinkedHashSet<>(request.participantIds());
        members.add(me);

        if (members.size() > MAX_PARTICIPANTS) {
            throw new ChatServiceException(
                    "Conversation cannot exceed " + MAX_PARTICIPANTS + " participants",
                    HttpStatus.BAD_REQUEST);
        }

        List<User> users = userRepository.findAllById(members);
        requireAllFound(users, members);

        Conversation conversation =
                conversationRepository.save(new Conversation(request.title(), me));

        for (Long userId : members) {
            participantRepository.save(new ConversationParticipant(conversation.getId(), userId));
        }

        List<ConversationCreatedPayload.ParticipantInfoDto> roster = users.stream()
                .map(u -> new ConversationCreatedPayload.ParticipantInfoDto(u.getId(), u.getUsername()))
                .toList();

        outboxRecorder.record(conversation.getId(), new ConversationCreatedPayload(
                List.copyOf(members), request.title(), me, roster));
        return toResponse(conversation, users);
    }

    @Transactional
    public void addParticipant(Long conversationId, Long me, Long userId) {
        requireParticipant(conversationId, me);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ChatServiceException("User " + userId + " not found", HttpStatus.NOT_FOUND));

        if (participantRepository.countById_ConversationId(conversationId) >= MAX_PARTICIPANTS) {
            throw new ChatServiceException(
                    "Conversation cannot exceed " + MAX_PARTICIPANTS + " participants",
                    HttpStatus.CONFLICT);
        }

        int inserted = participantRepository.insertIfAbsent(conversationId, userId);
        if (inserted == 0) {
            return;
        }
        outboxRecorder.record(conversationId, new ParticipantAddedPayload(
                participantRepository.findUserIds(conversationId), userId, user.getUsername()));
    }

    @Transactional
    public void leave(Long conversationId, Long me) {
        Conversation conversation = requireParticipant(conversationId, me);

        if (conversation.getAdminId().equals(me)) {
            throw new ChatServiceException(
                    "Admin must transfer rights or delete the conversation before leaving",
                    HttpStatus.CONFLICT);
        }

        List<Long> recipients = participantRepository.findUserIds(conversationId);
        participantRepository.deleteById(new ParticipantId(conversationId, me));
        outboxRecorder.record(conversationId, new ParticipantRemovedPayload(recipients, me, true));
    }

    @Transactional
    public void removeParticipant(Long conversationId, Long me, Long target) {
        Conversation conversation = requireAdmin(conversationId, me);

        if (conversation.getAdminId().equals(target)) {
            throw new ChatServiceException(
                    "Admin cannot be removed from the conversation", HttpStatus.CONFLICT);
        }

        ParticipantId id = new ParticipantId(conversationId, target);
        if (!participantRepository.existsById(id)) {
            return;
        }
        List<Long> recipients = participantRepository.findUserIds(conversationId);
        participantRepository.deleteById(id);
        outboxRecorder.record(conversationId, new ParticipantRemovedPayload(recipients, target, false));
    }

    @Transactional
    public void transferAdmin(Long conversationId, Long me, Long newAdminId) {
        Conversation conversation = requireAdmin(conversationId, me);

        if (!participantRepository.existsById(new ParticipantId(conversationId, newAdminId))) {
            throw new ChatServiceException(
                    "User " + newAdminId + " is not a participant", HttpStatus.BAD_REQUEST);
        }

        conversation.setAdminId(newAdminId);
        outboxRecorder.record(conversationId, new ConversationUpdatedPayload(
                participantRepository.findUserIds(conversationId),
                conversation.getTitle(), conversation.getAdminId()));
    }

    @Transactional
    public void rename(Long conversationId, Long me, String title) {
        Conversation conversation = requireParticipant(conversationId, me);
        conversation.setTitle(title);
        outboxRecorder.record(conversationId, new ConversationUpdatedPayload(
                participantRepository.findUserIds(conversationId),
                conversation.getTitle(), conversation.getAdminId()));
    }

    @Transactional
    public void delete(Long conversationId, Long me) {
        requireAdmin(conversationId, me);

        List<Long> recipients = participantRepository.findUserIds(conversationId);
        outboxRecorder.record(conversationId, new ConversationDeletedPayload(recipients));
        conversationRepository.deleteById(conversationId);
    }

    @Transactional(readOnly = true)
    public List<ConversationSummaryResponse> list(Long me, int page) {
        List<ConversationSummaryRow> rows = participantRepository
                .findSummaries(me, PageRequest.of(page, PAGE_SIZE));
        if (rows.isEmpty()) {
            return List.of();
        }

        List<Long> ids = rows.stream().map(ConversationSummaryRow::id).toList();

        Map<Long, List<UserResponse>> preview = participantRepository.findParticipantRows(ids, me)
                .stream()
                .collect(Collectors.groupingBy(
                        ParticipantRow::conversationId,
                        LinkedHashMap::new,
                        Collectors.mapping(
                                r -> new UserResponse(r.userId(), r.username()),
                                Collectors.collectingAndThen(
                                        Collectors.toList(),
                                        list -> list.subList(0, Math.min(PREVIEW_SIZE, list.size()))))));

        return rows.stream()
                .map(r -> r.toResponse(preview.getOrDefault(r.id(), List.of())))
                .toList();
    }

    private Conversation requireParticipant(Long conversationId, Long userId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> notFound(conversationId));

        if (!participantRepository.existsById(new ParticipantId(conversationId, userId))) {
            throw notFound(conversationId);   // 404, а не 403: не світимо існування чату
        }
        return conversation;
    }

    private Conversation requireAdmin(Long conversationId, Long userId) {
        Conversation conversation = requireParticipant(conversationId, userId);
        if (!conversation.getAdminId().equals(userId)) {
            throw new ChatServiceException(
                    "Only the conversation admin can perform this action", HttpStatus.FORBIDDEN);
        }
        return conversation;
    }

    private ChatServiceException notFound(Long conversationId) {
        return new ChatServiceException(
                "Conversation " + conversationId + " not found", HttpStatus.NOT_FOUND);
    }

    private void requireAllFound(List<User> found, Set<Long> requested) {
        if (found.size() == requested.size()) {
            return;
        }
        Set<Long> missing = new LinkedHashSet<>(requested);
        found.forEach(u -> missing.remove(u.getId()));
        throw new ChatServiceException("Users not found: " + missing, HttpStatus.NOT_FOUND);
    }

    private ConversationResponse toResponse(Conversation conversation, List<User> participants) {
        return new ConversationResponse(
                conversation.getId(),
                conversation.getTitle(),
                conversation.getAdminId(),
                participants.stream().map(u -> new UserResponse(u.getId(), u.getUsername())).toList(),
                conversation.getCreatedAt(),
                conversation.getLastMessageAt()
        );
    }
}