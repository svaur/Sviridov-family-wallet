package ru.sviridov.wallet.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import ru.sviridov.wallet.domain.Admin;
import ru.sviridov.wallet.domain.AdminRepository;

/**
 * Creates the initial admin from env vars if it does not exist yet. The first
 * login is forced to change the env-supplied password ({@code must_change_password}).
 */
@Component
public class AdminBootstrap implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${ADMIN_USERNAME:}")
    private String adminUsername;

    @Value("${ADMIN_PASSWORD:}")
    private String adminPassword;

    public AdminBootstrap(AdminRepository adminRepository, PasswordEncoder passwordEncoder) {
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (adminUsername == null || adminUsername.isBlank()
                || adminPassword == null || adminPassword.isBlank()) {
            return;
        }
        if (adminPassword.length() < 8) {
            log.error("ADMIN_PASSWORD must be at least 8 characters; admin not created");
            return;
        }
        String username = adminUsername.trim();
        adminRepository.findByUsername(username).ifPresentOrElse(
                existing -> log.info("Admin '{}' already exists, skipping bootstrap", existing.getUsername()),
                () -> {
                    adminRepository.save(new Admin(username, passwordEncoder.encode(adminPassword), true));
                    log.info("Created admin '{}' from ADMIN_USERNAME/ADMIN_PASSWORD", username);
                });
    }
}
