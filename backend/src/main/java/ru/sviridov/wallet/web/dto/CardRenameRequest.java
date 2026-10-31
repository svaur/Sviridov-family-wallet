package ru.sviridov.wallet.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CardRenameRequest(
        @NotBlank(message = "Название карты обязательно")
        @Size(max = 120, message = "Название карты слишком длинное")
        String displayName) {
}
