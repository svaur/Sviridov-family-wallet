package ru.sviridov.wallet.web.dto;

import jakarta.validation.constraints.Size;

/**
 * Editable part of the profile. Both names are optional: an empty string (or a
 * missing field) clears the name and the sidebar falls back to the login.
 */
public record ProfileRequest(
        @Size(max = 64, message = "Имя не должно быть длиннее 64 символов") String firstName,
        @Size(max = 64, message = "Фамилия не должна быть длиннее 64 символов") String lastName) {
}
