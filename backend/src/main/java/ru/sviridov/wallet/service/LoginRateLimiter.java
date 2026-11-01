package ru.sviridov.wallet.service;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Simple in-memory sliding-window rate limiter for login attempts. Keeps the
 * last {@link #MAX_ATTEMPTS} failure timestamps per key (username + client IP)
 * and blocks the key while more than {@link #MAX_ATTEMPTS} failures happened
 * within {@link #WINDOW}. Successful login clears the key.
 *
 * <p>State is per process on purpose (one backend instance, a handful of
 * users): a restart forgets the counters and a second instance would count
 * separately. The map stays bounded — a key is dropped as soon as its window
 * has fully expired, and {@link #sweep()} runs once the map grows past
 * {@link #MAX_KEYS}, so probing with random usernames cannot leak memory.</p>
 */
@Component
public class LoginRateLimiter {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration WINDOW = Duration.ofMinutes(5);
    /** Upper bound on tracked keys; above it expired keys are swept. */
    static final int MAX_KEYS = 10_000;

    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginRateLimiter() {
        this(Clock.systemUTC());
    }

    LoginRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public boolean isBlocked(String key) {
        Deque<Instant> q = failures.get(key);
        if (q == null) {
            return false;
        }
        synchronized (q) {
            evictOld(q);
            if (q.isEmpty()) {
                // Nothing left in the window: drop the entry instead of keeping
                // an empty deque alive until the process restarts.
                failures.remove(key, q);
                return false;
            }
            return q.size() >= MAX_ATTEMPTS;
        }
    }

    public void recordFailure(String key) {
        Deque<Instant> q = failures.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());
        synchronized (q) {
            q.addLast(Instant.now(clock));
            evictOld(q);
        }
        if (failures.size() > MAX_KEYS) {
            sweep();
        }
    }

    public void reset(String key) {
        failures.remove(key);
    }

    /** Drops every key whose whole window has expired. */
    void sweep() {
        Instant cutoff = Instant.now(clock).minus(WINDOW);
        failures.forEach((key, q) -> {
            synchronized (q) {
                if (q.isEmpty() || q.peekLast().isBefore(cutoff)) {
                    failures.remove(key, q);
                }
            }
        });
    }

    /** Number of keys currently tracked; test-only visibility. */
    int trackedKeys() {
        return failures.size();
    }

    private void evictOld(Deque<Instant> q) {
        Instant cutoff = Instant.now(clock).minus(WINDOW);
        while (!q.isEmpty() && q.peekFirst().isBefore(cutoff)) {
            q.pollFirst();
        }
    }
}
