package com.yurii.zhuravlov.chatservice.controller;

import com.yurii.zhuravlov.chatservice.dto.request.AddParticipantRequest;
import com.yurii.zhuravlov.chatservice.dto.request.CreateConversationRequest;
import com.yurii.zhuravlov.chatservice.dto.request.RenameConversationRequest;
import com.yurii.zhuravlov.chatservice.dto.request.TransferAdminRequest;
import com.yurii.zhuravlov.chatservice.dto.response.ConversationResponse;
import com.yurii.zhuravlov.chatservice.dto.response.ConversationSummaryResponse;
import com.yurii.zhuravlov.chatservice.security.CurrentUserId;
import com.yurii.zhuravlov.chatservice.service.ConversationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
@Validated
public class ConversationController {

    private final ConversationService conversationService;

    @PostMapping
    public ResponseEntity<ConversationResponse> create(
            @Valid @RequestBody CreateConversationRequest request,
            @CurrentUserId Long userId) {

        ConversationResponse body = conversationService.create(userId, request);
        return ResponseEntity.created(URI.create("/api/conversations/" + body.id())).body(body);
    }

    @PostMapping("/{id}/participants")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addParticipant(@PathVariable Long id,
                               @Valid @RequestBody AddParticipantRequest request,
                               @CurrentUserId Long userId) {
        conversationService.addParticipant(id, userId, request.userId());
    }

    @DeleteMapping("/{id}/participants/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(@PathVariable Long id, @CurrentUserId Long userId) {
        conversationService.leave(id, userId);
    }

    @DeleteMapping("/{id}/participants/{targetId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeParticipant(@PathVariable Long id,
                                  @PathVariable Long targetId,
                                  @CurrentUserId Long userId) {
        conversationService.removeParticipant(id, userId, targetId);
    }

    @PutMapping("/{id}/admin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void transferAdmin(@PathVariable Long id,
                              @Valid @RequestBody TransferAdminRequest request,
                              @CurrentUserId Long userId) {
        conversationService.transferAdmin(id, userId, request.userId());
    }

    @PatchMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void rename(@PathVariable Long id,
                       @Valid @RequestBody RenameConversationRequest request,
                       @CurrentUserId Long userId) {
        conversationService.rename(id, userId, request.title());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @CurrentUserId Long userId) {
        conversationService.delete(id, userId);
    }

    @GetMapping
    public List<ConversationSummaryResponse> list(@RequestParam(defaultValue = "0") @Min(0) int page,
                                                  @CurrentUserId Long userId){
        return conversationService.list(userId, page);
    }
}