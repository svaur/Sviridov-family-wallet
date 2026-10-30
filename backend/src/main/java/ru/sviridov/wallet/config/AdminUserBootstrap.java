package ru.sviridov.wallet.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import ru.sviridov.wallet.domain.User;
import ru.sviridov.wallet.domain.UserRepository;

/**
 * Creates an initial user from env vars if it does not exist yet.
 * Users are otherwise created manually (SQL / CLI) — there is no self-registration.
 */
@Component
public class AdminUserBootstrap implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminUserBootstrap.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${BOOTSTRAP_USERNAME:}")
    private String bootstrapUsername;

    @Value("${BOOTSTRAP_PASSWORD:}")
    private String bootstrapPassword;

    public AdminUserBootstrap(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (bootstrapUsername == null || bootstrapUsername.isBlank()
                || bootstrapPassword == null || bootstrapPassword.isBlank()) {
            return;
        }
        if (bootstrapPassword.length() < 8) {
            log.error("BOOTSTRAP_PASSWORD must be at least 8 characters; user not created");
            return;
        }
        String username = bootstrapUsername.trim();
        userRepository.findByUsername(username).ifPresentOrElse(
                existing -> log.info("User '{}' already exists, skipping bootstrap", existing.getUsername()),
                () -> {
                    userRepository.save(new User(username, passwordEncoder.encode(bootstrapPassword)));
                    log.info("Created user '{}' from BOOTSTRAP_USERNAME/BOOTSTRAP_PASSWORD", username);
                });
    }
}
