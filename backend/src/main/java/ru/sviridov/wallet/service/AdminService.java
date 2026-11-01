package ru.sviridov.wallet.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sviridov.wallet.domain.Admin;
import ru.sviridov.wallet.domain.AdminRepository;
import ru.sviridov.wallet.web.NotFoundException;
import ru.sviridov.wallet.web.dto.AdminResponse;

import java.time.Instant;

/**
 * Admin-panel account operations (login bookkeeping and, later, the admin's own
 * password change). User management lives in {@code AdminUserService} instead.
 */
@Service
public class AdminService {

    private final AdminRepository adminRepository;

    public AdminService(AdminRepository adminRepository) {
        this.adminRepository = adminRepository;
    }

    @Transactional(readOnly = true)
    public AdminResponse get(Long adminId) {
        return AdminResponse.from(load(adminId));
    }

    @Transactional
    public void recordLogin(Long adminId) {
        load(adminId).setLastLoginAt(Instant.now());
    }

    private Admin load(Long adminId) {
        return adminRepository.findById(adminId)
                .orElseThrow(() -> new NotFoundException("Администратор не найден"));
    }
}
