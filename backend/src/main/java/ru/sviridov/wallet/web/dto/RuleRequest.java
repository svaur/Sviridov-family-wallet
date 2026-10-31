package ru.sviridov.wallet.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.Map;

public record RuleRequest(
        @NotBlank
        @Pattern(regexp = "merchant_contains|merchant_regex|amount_range|day_of_month",
                message = "Некорректный тип правила")
        String type,
        Map<String, Object> params,
        @NotNull Long categoryId,
        Integer priority) {
}
