package ru.sviridov.wallet.web.dto;

import jakarta.validation.constraints.NotNull;

/** Body for manually assigning a category to an existing transaction. */
public record CategoryAssignRequest(@NotNull Long categoryId) {
}
