package com.example.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "用户名不能为空")
        @Size(min = 2, max = 20, message = "用户名长度2-20位")
        String username,
        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 32, message = "密码长度6-32位")
        String password
) {
}
