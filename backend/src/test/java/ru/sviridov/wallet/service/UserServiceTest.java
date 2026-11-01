package ru.sviridov.wallet.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import ru.sviridov.wallet.domain.User;
import ru.sviridov.wallet.domain.UserRepository;
import ru.sviridov.wallet.web.BadRequestException;
import ru.sviridov.wallet.web.NotFoundException;
import ru.sviridov.wallet.web.TooManyRequestsException;
import ru.sviridov.wallet.web.dto.UserResponse;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final Long USER_ID = 7L;
    private static final String CURRENT = "old-secret-1";

    @Mock
    private UserRepository userRepository;

    @Mock
    private LoginRateLimiter rateLimiter;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private UserService service() {
        return new UserService(userRepository, passwordEncoder, rateLimiter);
    }

    private User existingUser() {
        User user = new User("ivan", passwordEncoder.encode(CURRENT));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        return user;
    }

    @Test
    void getReturnsTheNameOfTheSignedInUser() {
        User user = existingUser();
        user.setName("Иван", "Свиридов");

        UserResponse response = service().get(USER_ID);

        assertThat(response.username()).isEqualTo("ivan");
        assertThat(response.firstName()).isEqualTo("Иван");
        assertThat(response.lastName()).isEqualTo("Свиридов");
    }

    @Test
    void getThrowsNotFoundForUnknownUser() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().get(USER_ID)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void updateProfileTrimsAndCollapsesSpaces() {
        User user = existingUser();

        UserResponse response = service().updateProfile(USER_ID, "  Иван   Петрович ", " Свиридов  ");

        assertThat(response.firstName()).isEqualTo("Иван Петрович");
        assertThat(response.lastName()).isEqualTo("Свиридов");
        assertThat(user.getFirstName()).isEqualTo("Иван Петрович");
    }

    @Test
    void updateProfileStoresNullForBlankNames() {
        User user = existingUser();

        service().updateProfile(USER_ID, "   ", "");

        assertThat(user.getFirstName()).isNull();
        assertThat(user.getLastName()).isNull();
    }

    @Test
    void changePasswordRejectsWrongCurrentPasswordAndCountsTheFailure() {
        existingUser();

        assertThatThrownBy(() -> service().changePassword(USER_ID, "not-my-password", "new-secret-2"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Неверный текущий пароль");
        verify(rateLimiter).recordFailure("password|7");
    }

    @Test
    void changePasswordRejectsTheCurrentPasswordAsNew() {
        existingUser();

        assertThatThrownBy(() -> service().changePassword(USER_ID, CURRENT, CURRENT))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Новый пароль должен отличаться от текущего");
    }

    @Test
    void changePasswordReplacesTheHashAndBumpsTheEpoch() {
        User user = existingUser();
        Instant epochBefore = user.getSessionEpoch();

        service().changePassword(USER_ID, CURRENT, "new-secret-2");

        assertThat(passwordEncoder.matches("new-secret-2", user.getPasswordHash())).isTrue();
        assertThat(user.getSessionEpoch()).isAfter(epochBefore);
        verify(rateLimiter).reset("password|7");
    }

    @Test
    void changePasswordIsBlockedAfterTooManyFailures() {
        when(rateLimiter.isBlocked("password|7")).thenReturn(true);

        assertThatThrownBy(() -> service().changePassword(USER_ID, CURRENT, "new-secret-2"))
                .isInstanceOf(TooManyRequestsException.class);
        verify(userRepository, never()).findById(any());
    }

    @Test
    void updateProfileDoesNotTouchThePassword() {
        User user = existingUser();
        String hashBefore = user.getPasswordHash();

        service().updateProfile(USER_ID, "Иван", null);

        // JPA dirty checking persists the entity; an explicit save would be
        // redundant and could mask a misplaced flush.
        verify(userRepository, never()).save(any());
        assertThat(user.getPasswordHash()).isEqualTo(hashBefore);
    }

    @Test
    void loginRateLimiterIsNotConsultedForProfileUpdates() {
        existingUser();

        service().updateProfile(USER_ID, "Иван", "Свиридов");

        verify(rateLimiter, never()).recordFailure(any());
        verify(rateLimiter, never()).isBlocked(any());
    }

    @Test
    void getUsesTheRepositoryOnce() {
        existingUser();

        service().get(USER_ID);

        verify(userRepository).findById(USER_ID);
    }
}
