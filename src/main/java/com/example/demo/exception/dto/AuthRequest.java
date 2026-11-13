package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AuthRequest(
        @NotBlank(message = "Имя пользователя не может быть пустым")
        @Size(min = 3, max = 50, message = "Длина имени должна быть от 3 до 50 символов")
        String username,

        @NotBlank(message = "Пароль не может быть пустым")
        @Size(min = 6, max = 100, message = "Пароль должен содержать минимум 6 символов")
        String password
) {
}
