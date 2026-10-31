package ru.sviridov.wallet.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Payload for creating a transaction manually. {@code source} is the funding
 * source (card / cash / other); {@code cardId} is required only when
 * {@code source} is "card". {@code purpose} (Назначение платежа) and
 * {@code categoryId} are optional — when no category is chosen the rule set is
 * applied automatically.
 */
public record TransactionRequest(
        @NotBlank(message = "Источник обязателен")
        @Pattern(regexp = "card|cash|other", message = "Источник должен быть card, cash или other") String source,
        Long cardId,
        @NotNull(message = "Дата обязательна") LocalDate date,
        @NotBlank(message = "Тип операции обязателен")
        @Pattern(regexp = "debit|credit", message = "Тип операции должен быть debit или credit") String type,
        @NotNull(message = "Сумма обязательна")
        @Positive(message = "Сумма должна быть больше нуля") BigDecimal amount,
        String purpose,
        Long categoryId) {
}
