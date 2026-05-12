package com.example.chat.dto;

public record ChatResponse(
        String content,
        String conversationId,
        String model
) {
    public static ChatResponse of(String content, String conversationId, String model) {
        return new ChatResponse(content, conversationId, model);
    }
}
