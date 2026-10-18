package com.emart.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Simple brute-force protection for password login: after {@value #MAX_FAILURES} failed attempts for the same
 * account within {@link #WINDOW}, further attempts are refused until the oldest failure ages out.
 *
 * <p>Deliberately minimal: state lives in memory (fine for the single-instance in-memory-database deployment, lost
 * on restart) and is keyed by e-mail only. Trade-off: someone can lock a known account out for a few minutes by
 * failing on purpose; the alternative of no limit lets them guess passwords freely.
 */
@Service
public class LoginAttemptService {

    static final int MAX_FAILURES = 5;
    static final Duration WINDOW = Duration.ofMinutes(10);
    /** Upper bound on tracked accounts, so random e-mails cannot grow the map without limit. */
    static final int MAX_TRACKED = 10_000;

    private final Clock clock;
    private final Map<String, Deque<Instant>> failures = new HashMap<>();

    @Autowired
    public LoginAttemptService() {
        this(Clock.systemUTC());
    }

    LoginAttemptService(Clock clock) {
        this.clock = clock;
    }

    public synchronized boolean isBlocked(String key) {
        Deque<Instant> recent = prune(key);
        return recent != null && recent.size() >= MAX_FAILURES;
    }

    /** How long until the account may try again (zero if it is not blocked). */
    public synchronized Duration retryAfter(String key) {
        Deque<Instant> recent = prune(key);
        if (recent == null || recent.size() < MAX_FAILURES) {
            return Duration.ZERO;
        }
        return Duration.between(clock.instant(), recent.peekFirst().plus(WINDOW));
    }

    public synchronized void recordFailure(String key) {
        if (failures.size() >= MAX_TRACKED && !failures.containsKey(key)) {
            evictExpired();
            if (failures.size() >= MAX_TRACKED) {
                failures.clear(); // under sustained abuse prefer forgetting over unbounded memory
            }
        }
        failures.computeIfAbsent(key, k -> new ArrayDeque<>()).addLast(clock.instant());
        prune(key);
    }

    public synchronized void reset(String key) {
        failures.remove(key);
    }

    synchronized int trackedKeys() {
        return failures.size();
    }

    private Deque<Instant> prune(String key) {
        Deque<Instant> recent = failures.get(key);
        if (recent == null) {
            return null;
        }
        Instant cutoff = clock.instant().minus(WINDOW);
        while (!recent.isEmpty() && !recent.peekFirst().isAfter(cutoff)) {
            recent.pollFirst();
        }
        if (recent.isEmpty()) {
            failures.remove(key);
            return null;
        }
        return recent;
    }

    private void evictExpired() {
        Instant cutoff = clock.instant().minus(WINDOW);
        failures.values().removeIf(d -> d.isEmpty() || !d.peekLast().isAfter(cutoff));
    }
}
