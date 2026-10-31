package ru.sviridov.wallet.web.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record RuleRequest(
        Map<String, Object> params,
        @NotNull Long categoryId,
        Integer priority) {
}
