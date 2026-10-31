package ru.sviridov.wallet.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record RuleRequest(
        @Size(max = 120) String name,
        Map<String, Object> params,
        @NotNull Long categoryId,
        Integer priority) {
}
