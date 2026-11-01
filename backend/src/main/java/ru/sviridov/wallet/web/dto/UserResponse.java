package ru.sviridov.wallet.web.dto;

import ru.sviridov.wallet.domain.User;

/**
 * The signed-in user as the UI sees it. {@code firstName}/{@code lastName} are
 * null until the user fills them in on the settings page; the sidebar falls
 * back to {@code username}. {@code mustChangePassword} drives the forced
 * password change after an admin reset.
 */
public record UserResponse(Long id, String username, String firstName, String lastName,
                           boolean mustChangePassword) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getFirstName(), user.getLastName(),
                user.isMustChangePassword());
    }
}
