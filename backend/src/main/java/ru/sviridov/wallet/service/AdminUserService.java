package ru.sviridov.wallet.service;

import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sviridov.wallet.domain.User;
import ru.sviridov.wallet.domain.UserRepository;
import ru.sviridov.wallet.web.BadRequestException;
import ru.sviridov.wallet.web.NotFoundException;
import ru.sviridov.wallet.web.dto.ResetPasswordResponse;
import ru.sviridov.wallet.web.dto.UserAdminResponse;

import java.security.SecureRandom;
import java.util.List;

/**
 * Admin operations on wallet users: list, create, ban/unban, reset the
 * password, force a logout and delete (with all data). Every mutating method
 * records an audit entry (actor = the acting admin, target = the user).
 *
 * <p>Banning and unbanning both bump the epoch: ban = {@code is_active=false}
 * (a future login is rejected) plus killing the current sessions; unban = the
 * reverse. A password reset reuses {@link User#changePassword} (which bumps the
 * epoch and clears the must-change flag) and then re-arms the flag.</p>
 */
@Service
public class AdminUserService {

    private static final String PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final int TEMP_PASSWORD_LENGTH = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;

    public AdminUserService(UserRepository userRepository, PasswordEncoder passwordEncoder, AuditService audit) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<UserAdminResponse> list() {
        return userRepository.findAll(Sort.by(Sort.Direction.ASC, "username")).stream()
                .map(UserAdminResponse::from)
                .toList();
    }

    @Transactional
    public UserAdminResponse create(Long actorId, String ip, String username, String password,
                                    String firstName, String lastName) {
        if (userRepository.findByUsername(username).isPresent()) {
            throw new BadRequestException("Пользователь с таким логином уже существует");
        }
        User user = new User(username, passwordEncoder.encode(password));
        user.setName(normalize(firstName), normalize(lastName));
        // The admin chose the initial password, so the user must change it.
        user.setMustChangePassword(true);
        User saved = userRepository.save(user);
        audit.record("user", saved.getId(), "create", saved.getUsername(), actorId, ip);
        return UserAdminResponse.from(saved);
    }

    @Transactional
    public void setActive(Long actorId, String ip, Long userId, boolean active) {
        User user = load(userId);
        user.setActive(active);
        user.bumpSessionEpoch();
        audit.record("user", user.getId(), active ? "unban" : "ban", user.getUsername(), actorId, ip);
    }

    @Transactional
    public ResetPasswordResponse resetPassword(Long actorId, String ip, Long userId) {
        User user = load(userId);
        String tempPassword = generateTempPassword();
        // changePassword() bumps the epoch (signs out everywhere) and clears the
        // flag; re-arming it is what turns this into a forced change.
        user.changePassword(passwordEncoder.encode(tempPassword));
        user.setMustChangePassword(true);
        audit.record("user", user.getId(), "reset_password", user.getUsername(), actorId, ip);
        return new ResetPasswordResponse(tempPassword);
    }

    @Transactional
    public void forceLogout(Long actorId, String ip, Long userId) {
        User user = load(userId);
        user.bumpSessionEpoch();
        audit.record("user", user.getId(), "force_logout", user.getUsername(), actorId, ip);
    }

    @Transactional
    public void delete(Long actorId, String ip, Long userId) {
        User user = load(userId);
        String username = user.getUsername();
        userRepository.delete(user);
        // Record after the delete, but with the username captured before it and
        // entityId = the id: the FK (userId) is left null so the entry survives.
        audit.record("user", user.getId(), "delete", username, actorId, ip);
    }

    private User load(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Пользователь не найден"));
    }

    /**
     * Unambiguous, mixed-case alphabet (no 0/O, 1/l/I) so the temporary
     * password survives being read aloud or retyped.
     */
    private static String generateTempPassword() {
        char[] chars = new char[TEMP_PASSWORD_LENGTH];
        for (int i = 0; i < chars.length; i++) {
            chars[i] = PASSWORD_CHARS.charAt(RANDOM.nextInt(PASSWORD_CHARS.length()));
        }
        return new String(chars);
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim().replaceAll("\\s+", " ");
        return trimmed.isEmpty() ? null : trimmed;
    }
}
