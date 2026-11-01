package ru.sviridov.wallet.web;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.sviridov.wallet.security.AppUser;
import ru.sviridov.wallet.service.UserService;
import ru.sviridov.wallet.web.dto.PasswordChangeRequest;
import ru.sviridov.wallet.web.dto.ProfileRequest;
import ru.sviridov.wallet.web.dto.UserResponse;

/**
 * The signed-in user's own profile. Kept out of {@code /api/auth} because that
 * controller owns the session lifecycle (login/logout), not user data.
 */
@RestController
@RequestMapping("/api/me")
public class MeController {

    private final UserService userService;

    public MeController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public UserResponse me(@AuthenticationPrincipal AppUser user) {
        return userService.get(user.getId());
    }

    @PutMapping("/profile")
    public UserResponse updateProfile(@AuthenticationPrincipal AppUser user,
                                      @Valid @RequestBody ProfileRequest request) {
        return userService.updateProfile(user.getId(), request.firstName(), request.lastName());
    }

    /**
     * Changes the password and bumps the epoch, so every session — including
     * this one — is rejected on its next request.
     */
    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal AppUser user,
                               @Valid @RequestBody PasswordChangeRequest request) {
        userService.changePassword(user.getId(), request.currentPassword(), request.newPassword());
    }
}
