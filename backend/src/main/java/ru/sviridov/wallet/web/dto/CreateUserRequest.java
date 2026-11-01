package ru.sviridov.wallet.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Admin creates a user with an initial password. The user is forced to change
 * it on first login ({@code must_change_password} is set by the service).
 */
public record CreateUserRequest(
        @NotBlank @Size(max = 64) String username,
        @NotBlank @Size(min = 8, max = 72) String password,
        @Size(max = 64) String firstName,
        @Size(max = 64) String lastName) {
}
