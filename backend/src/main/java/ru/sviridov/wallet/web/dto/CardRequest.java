package ru.sviridov.wallet.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CardRequest(
        @NotBlank(message = "Название карты обязательно")
        @Size(max = 120, message = "Название карты слишком длинное")
        String displayName,

        @Size(max = 120, message = "Название банка слишком длинное")
        String bank,

        @Pattern(regexp = "\\d{0,4}", message = "last4 должен содержать до 4 цифр")
        String last4,

        @Size(max = 3, message = "Валюта — до 3 символов")
        String currency,

        Boolean archived) {
}
