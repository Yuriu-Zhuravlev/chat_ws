package com.yurii.zhuravlov.chatservice.dto.response;

import java.util.List;

public record MessagePageResponse(List<MessageResponse> messages, boolean hasMore) {}