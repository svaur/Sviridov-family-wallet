package ru.sviridov.wallet.web.dto;

/**
 * The one-time password an admin hands to a user after a reset. It is returned
 * once and never stored in plain text — only its BCrypt hash lands in the DB.
 */
public record ResetPasswordResponse(String tempPassword) {
}
