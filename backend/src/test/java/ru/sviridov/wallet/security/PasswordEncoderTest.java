package ru.sviridov.wallet.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordEncoderTest {

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    @Test
    void encodesAndMatches() {
        String raw = "secret123";
        String encoded = encoder.encode(raw);

        assertThat(encoded).startsWith("$2");
        assertThat(encoded).isNotEqualTo(raw);
        assertThat(encoder.matches(raw, encoded)).isTrue();
        assertThat(encoder.matches("wrong", encoded)).isFalse();
    }

    @Test
    void encodesAreSalted() {
        assertThat(encoder.encode("secret123")).isNotEqualTo(encoder.encode("secret123"));
    }
}
