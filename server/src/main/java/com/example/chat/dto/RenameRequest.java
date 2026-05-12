package com.example.chat.dto;

import jakarta.validation.constraints.NotBlank;

public record RenameRequest(
        @NotBlank(message = "标题不能为空")
        String title
) {
}
