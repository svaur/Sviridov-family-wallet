package ru.sviridov.wallet.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Create/edit payload for a card. {@code cardNumber} is required on create and
 * optional on update: the statements page's inline rename sends only
 * {@code displayName}, and the number must then be kept as-is.
 */
public record CardRequest(
        @Size(max = 20, message = "Номер карты не длиннее 20 символов")
        String cardNumber,

        @NotBlank(message = "Название карты обязательно")
        @Size(max = 120, message = "Название карты слишком длинное")
        String displayName) {
}
