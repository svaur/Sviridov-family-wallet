package ru.sviridov.wallet.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sviridov.wallet.domain.User;
import ru.sviridov.wallet.domain.UserRepository;
import ru.sviridov.wallet.web.BadRequestException;
import ru.sviridov.wallet.web.NotFoundException;
import ru.sviridov.wallet.web.TooManyRequestsException;
import ru.sviridov.wallet.web.dto.UserResponse;

/**
 * Profile and credential operations of the signed-in user.
 *
 * <p>Changing the password bumps {@code users.password_changed_at}. Sessions
 * carry the value they saw at login, and {@link ru.sviridov.wallet.security.PasswordEpochFilter}
 * rejects the stale ones — that is the whole "sign out everywhere" mechanism,
 * and it needs no server-side session registry.</p>
 */
@Service
public class UserService {

    /** Same window as login attempts, but per user: the attacker already has a session. */
    private static final String PASSWORD_ATTEMPT_PREFIX = "password|";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginRateLimiter rateLimiter;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, LoginRateLimiter rateLimiter) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.rateLimiter = rateLimiter;
    }

    @Transactional(readOnly = true)
    public UserResponse get(Long userId) {
        return UserResponse.from(load(userId));
    }

    @Transactional
    public UserResponse updateProfile(Long userId, String firstName, String lastName) {
        User user = load(userId);
        user.setName(normalizeName(firstName), normalizeName(lastName));
        return UserResponse.from(user);
    }

    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        String key = PASSWORD_ATTEMPT_PREFIX + userId;
        if (rateLimiter.isBlocked(key)) {
            throw new TooManyRequestsException("Слишком много неверных попыток. Попробуйте через несколько минут.");
        }
        User user = load(userId);
        // Verify the current password first: the new-password rules must not be
        // usable as an oracle by someone who only stole the cookie.
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            rateLimiter.recordFailure(key);
            throw new BadRequestException("Неверный текущий пароль");
        }
        if (newPassword.equals(currentPassword)) {
            throw new BadRequestException("Новый пароль должен отличаться от текущего");
        }
        rateLimiter.reset(key);
        user.changePassword(passwordEncoder.encode(newPassword));
    }

    private User load(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Пользователь не найден"));
    }

    /**
     * Names are display data: trim what the user typed and store nothing rather
     * than an empty string, so "no name" has exactly one representation.
     */
    private static String normalizeName(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim().replaceAll("\\s+", " ");
        return trimmed.isEmpty() ? null : trimmed;
    }
}
