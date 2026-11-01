package ru.sviridov.wallet.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.sviridov.wallet.domain.Admin;
import ru.sviridov.wallet.domain.AdminRepository;
import ru.sviridov.wallet.web.NotFoundException;
import ru.sviridov.wallet.web.dto.AdminResponse;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    private static final Long ADMIN_ID = 7L;

    @Mock
    private AdminRepository adminRepository;

    private AdminService service() {
        return new AdminService(adminRepository);
    }

    private Admin existingAdmin() {
        Admin admin = new Admin("root", "hash", true);
        when(adminRepository.findById(ADMIN_ID)).thenReturn(Optional.of(admin));
        return admin;
    }

    @Test
    void getReturnsTheAdmin() {
        existingAdmin();

        AdminResponse response = service().get(ADMIN_ID);

        assertThat(response.username()).isEqualTo("root");
        assertThat(response.mustChangePassword()).isTrue();
    }

    @Test
    void getThrowsWhenUnknown() {
        when(adminRepository.findById(ADMIN_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().get(ADMIN_ID))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Администратор не найден");
    }

    @Test
    void recordLoginStampsLastLoginAt() {
        Admin admin = existingAdmin();

        service().recordLogin(ADMIN_ID);

        assertThat(admin.getLastLoginAt()).isNotNull();
    }
}
