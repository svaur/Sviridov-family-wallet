package ru.sviridov.wallet.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Body for editing the operation, amount and date of a transaction in a draft. */
public record DraftEditRequest(
        @NotBlank @Size(max = 200) String operation,
        @NotNull @Positive BigDecimal amount,
        @NotNull LocalDate date) {
}
