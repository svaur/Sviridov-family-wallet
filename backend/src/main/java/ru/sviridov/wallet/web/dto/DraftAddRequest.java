package ru.sviridov.wallet.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Body for adding a transaction to a screenshot draft (source = statement). */
public record DraftAddRequest(
        @NotBlank @Size(max = 200) String operation,
        @NotNull @Positive BigDecimal amount,
        @NotBlank @Pattern(regexp = "debit|credit") String type,
        @NotNull LocalDate date) {
}
