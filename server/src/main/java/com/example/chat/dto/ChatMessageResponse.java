package com.example.chat.dto;

public record ChatMessageResponse(
        Long id,
        String role,
        String content
) {
}
