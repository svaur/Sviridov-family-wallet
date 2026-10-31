package ru.sviridov.wallet.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoginRateLimiterTest {

    private final LoginRateLimiter limiter = new LoginRateLimiter();

    @Test
    void notBlockedBeforeMaxAttempts() {
        String key = "user|1.2.3.4";
        for (int i = 0; i < 4; i++) {
            limiter.recordFailure(key);
        }
        assertThat(limiter.isBlocked(key)).isFalse();
    }

    @Test
    void blocksAfterMaxAttempts() {
        String key = "user|1.2.3.4";
        for (int i = 0; i < 5; i++) {
            limiter.recordFailure(key);
        }
        assertThat(limiter.isBlocked(key)).isTrue();
    }

    @Test
    void resetClearsBlock() {
        String key = "user|1.2.3.4";
        for (int i = 0; i < 5; i++) {
            limiter.recordFailure(key);
        }
        limiter.reset(key);
        assertThat(limiter.isBlocked(key)).isFalse();
    }

    @Test
    void keysAreIndependent() {
        limiter.recordFailure("a|1.1.1.1");
        limiter.recordFailure("a|1.1.1.1");
        limiter.recordFailure("a|1.1.1.1");
        limiter.recordFailure("a|1.1.1.1");
        limiter.recordFailure("a|1.1.1.1");
        assertThat(limiter.isBlocked("a|1.1.1.1")).isTrue();
        assertThat(limiter.isBlocked("b|2.2.2.2")).isFalse();
    }
}
