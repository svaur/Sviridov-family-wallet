package ru.sviridov.wallet.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import ru.sviridov.wallet.domain.User;
import ru.sviridov.wallet.domain.UserRepository;
import ru.sviridov.wallet.web.BadRequestException;
import ru.sviridov.wallet.web.NotFoundException;
import ru.sviridov.wallet.web.dto.ResetPasswordResponse;
import ru.sviridov.wallet.web.dto.UserAdminResponse;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    private static final Long USER_ID = 7L;
    private static final Long ACTOR_ID = 1L;
    private static final String IP = "127.0.0.1";

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditService auditService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private AdminUserService service() {
        return new AdminUserService(userRepository, passwordEncoder, auditService);
    }

    private User existingUser() {
        User user = new User("ivan", passwordEncoder.encode("old-secret-1"));
        // The entity's id is normally assigned by the DB; the mock must carry it
        // so audit assertions can name the target id.
        ReflectionTestUtils.setField(user, "id", USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        return user;
    }

    @Test
    void listMapsAndSortsByUsername() {
        User b = new User("bob", "h");
        User a = new User("alice", "h");
        when(userRepository.findAll(any(Sort.class))).thenReturn(List.of(a, b));

        List<UserAdminResponse> result = service().list();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).username()).isEqualTo("alice");
        assertThat(result.get(1).username()).isEqualTo("bob");
    }

    @Test
    void createHashesPasswordAndForcesChange() {
        when(userRepository.findByUsername("newbie")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserAdminResponse response = service().create(ACTOR_ID, IP, "newbie", "temp-secret-1", "  Иван ", "Свиридов");

        assertThat(response.username()).isEqualTo("newbie");
        assertThat(response.firstName()).isEqualTo("Иван");
        assertThat(response.lastName()).isEqualTo("Свиридов");
        assertThat(response.mustChangePassword()).isTrue();
        assertThat(response.active()).isTrue();
    }

    @Test
    void createRejectsDuplicateUsername() {
        when(userRepository.findByUsername("ivan")).thenReturn(Optional.of(new User("ivan", "h")));

        assertThatThrownBy(() -> service().create(ACTOR_ID, IP, "ivan", "temp-secret-1", null, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Пользователь с таким логином уже существует");
    }

    @Test
    void banSetsActiveFalse() {
        User user = existingUser();

        service().setActive(ACTOR_ID, IP, USER_ID, false);

        assertThat(user.isActive()).isFalse();
        verify(auditService).record("user", USER_ID, "ban", "ivan", ACTOR_ID, IP);
    }

    @Test
    void unbanSetsActiveTrue() {
        User user = existingUser();
        user.setActive(false);

        service().setActive(ACTOR_ID, IP, USER_ID, true);

        assertThat(user.isActive()).isTrue();
    }

    @Test
    void resetPasswordReturnsAWorkingTempPasswordAndForcesChange() {
        User user = existingUser();

        ResetPasswordResponse response = service().resetPassword(ACTOR_ID, IP, USER_ID);

        assertThat(response.tempPassword()).hasSize(12);
        assertThat(passwordEncoder.matches(response.tempPassword(), user.getPasswordHash())).isTrue();
        assertThat(user.isMustChangePassword()).isTrue();
    }

    @Test
    void forceLogoutBumpsTheEpoch() {
        User user = existingUser();
        Instant before = user.getSessionEpoch();

        service().forceLogout(ACTOR_ID, IP, USER_ID);

        assertThat(user.getSessionEpoch()).isNotEqualTo(before);
    }

    @Test
    void deleteRemovesTheUser() {
        User user = existingUser();

        service().delete(ACTOR_ID, IP, USER_ID);

        verify(userRepository).delete(user);
        verify(auditService).record("user", USER_ID, "delete", "ivan", ACTOR_ID, IP);
    }

    @Test
    void unknownUserThrowsNotFound() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().setActive(ACTOR_ID, IP, USER_ID, false))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Пользователь не найден");
    }
}
