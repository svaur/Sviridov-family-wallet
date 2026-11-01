package ru.sviridov.wallet.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Password change. The repeat field is checked in the browser only — sending it
 * would not add anything the server can trust.
 */
public record PasswordChangeRequest(
        @NotBlank(message = "Введите текущий пароль") String currentPassword,
        @NotBlank(message = "Введите новый пароль")
        @Size(min = 8, max = 72, message = "Пароль должен быть от 8 до 72 символов") String newPassword) {
}
