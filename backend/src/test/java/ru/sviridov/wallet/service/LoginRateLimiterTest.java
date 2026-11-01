package ru.sviridov.wallet.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

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

    @Test
    void expiredWindowUnblocksAndDropsTheKey() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        LoginRateLimiter limiter = new LoginRateLimiter(clock);
        String key = "user|1.2.3.4";
        for (int i = 0; i < 5; i++) {
            limiter.recordFailure(key);
        }
        assertThat(limiter.isBlocked(key)).isTrue();
        assertThat(limiter.trackedKeys()).isEqualTo(1);

        clock.advance(Duration.ofMinutes(6));

        assertThat(limiter.isBlocked(key)).isFalse();
        assertThat(limiter.trackedKeys()).isZero();
    }

    @Test
    void sweepDropsKeysThatNeverComeBack() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        LoginRateLimiter limiter = new LoginRateLimiter(clock);
        limiter.recordFailure("a|1.1.1.1");
        limiter.recordFailure("b|2.2.2.2");
        limiter.recordFailure("c|3.3.3.3");
        assertThat(limiter.trackedKeys()).isEqualTo(3);

        clock.advance(Duration.ofMinutes(6));
        limiter.sweep();

        assertThat(limiter.trackedKeys()).isZero();
    }

    @Test
    void sweepKeepsKeysWithFailuresInsideTheWindow() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        LoginRateLimiter limiter = new LoginRateLimiter(clock);
        limiter.recordFailure("a|1.1.1.1");

        clock.advance(Duration.ofMinutes(4));
        limiter.recordFailure("b|2.2.2.2");
        clock.advance(Duration.ofMinutes(2)); // "a" expired, "b" is 2 min old
        limiter.sweep();

        assertThat(limiter.trackedKeys()).isEqualTo(1);
        assertThat(limiter.isBlocked("b|2.2.2.2")).isFalse();
    }

    /** Clock that can be moved forward, so expiry is testable without sleeping. */
    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
