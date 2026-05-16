package com.example.chat.dto;

import java.util.List;

public record MessagePageResponse(
        List<ChatMessageResponse> messages,
        boolean hasMore,
        long total
) {
}
