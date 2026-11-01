package ru.sviridov.wallet.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Edits the user-visible fields of an existing transaction. Mirrors the inline
 * editing of the import preview: operation, amount and date only — the funding
 * source, type and category are changed by their own actions.
 */
public record TransactionEditRequest(
        @Size(max = 200, message = "Наименование не длиннее 200 символов")
        String operation,

        @NotNull(message = "Сумма обязательна")
        @Positive(message = "Сумма должна быть больше нуля")
        BigDecimal amount,

        @NotNull(message = "Дата обязательна")
        LocalDate date) {
}
