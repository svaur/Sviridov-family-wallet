package ru.sviridov.wallet.web.dto;

import ru.sviridov.wallet.domain.User;

import java.time.Instant;

/**
 * A user as the admin panel sees it. Includes the management flags
 * ({@code active}/{@code mustChangePassword}) and the last-login/creation
 * timestamps that the wallet-facing {@link UserResponse} deliberately omits.
 */
public record UserAdminResponse(Long id, String username, String firstName, String lastName,
                                boolean active, boolean mustChangePassword,
                                Instant lastLoginAt, Instant createdAt) {

    public static UserAdminResponse from(User user) {
        return new UserAdminResponse(user.getId(), user.getUsername(), user.getFirstName(), user.getLastName(),
                user.isActive(), user.isMustChangePassword(), user.getLastLoginAt(), user.getCreatedAt());
    }
}
