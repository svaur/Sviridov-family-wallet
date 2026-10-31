package ru.sviridov.wallet.service;

import org.springframework.stereotype.Component;

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
 */
@Component
public class LoginRateLimiter {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration WINDOW = Duration.ofMinutes(5);

    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    public boolean isBlocked(String key) {
        Deque<Instant> q = failures.get(key);
        if (q == null) {
            return false;
        }
        synchronized (q) {
            evictOld(q);
            return q.size() >= MAX_ATTEMPTS;
        }
    }

    public void recordFailure(String key) {
        Deque<Instant> q = failures.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());
        synchronized (q) {
            q.addLast(Instant.now());
            evictOld(q);
        }
    }

    public void reset(String key) {
        failures.remove(key);
    }

    private void evictOld(Deque<Instant> q) {
        Instant cutoff = Instant.now().minus(WINDOW);
        while (!q.isEmpty() && q.peekFirst().isBefore(cutoff)) {
            q.pollFirst();
        }
    }
}
