package com.yurii.zhuravlov.chatservice.dto.request;

public record MessagePageRequest(Long messageId, Boolean isBefore, Integer limit) {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;

    public MessagePageRequest {
        // Default direction is backwards: opening a chat loads the newest page.
        isBefore = (isBefore == null) || isBefore;
        limit = (limit == null || limit < 1) ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
    }
}