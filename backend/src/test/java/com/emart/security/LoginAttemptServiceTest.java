package com.emart.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class LoginAttemptServiceTest {

    /** A clock the test can move forward. */
    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-01-01T10:00:00Z");
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
        void advance(Duration d) { now = now.plus(d); }
    }

    final MutableClock clock = new MutableClock();
    final LoginAttemptService service = new LoginAttemptService(clock);

    @Test
    void blocksAfterFiveFailures_andReportsHowLongToWait() {
        for (int i = 0; i < 4; i++) service.recordFailure("a@b.com");
        assertFalse(service.isBlocked("a@b.com"), "4 failures are still allowed");
        service.recordFailure("a@b.com");
        assertTrue(service.isBlocked("a@b.com"));
        assertTrue(service.retryAfter("a@b.com").toSeconds() > 0);
        assertTrue(service.retryAfter("a@b.com").compareTo(Duration.ofMinutes(10)) <= 0);
    }

    @Test
    void theBlockExpiresAfterTheWindow() {
        for (int i = 0; i < 5; i++) service.recordFailure("a@b.com");
        assertTrue(service.isBlocked("a@b.com"));
        clock.advance(Duration.ofMinutes(10).plusSeconds(1));
        assertFalse(service.isBlocked("a@b.com"));
    }

    @Test
    void aSuccessfulLoginResetsTheCounter() {
        for (int i = 0; i < 4; i++) service.recordFailure("a@b.com");
        service.reset("a@b.com");
        service.recordFailure("a@b.com");
        assertFalse(service.isBlocked("a@b.com"));
    }

    @Test
    void accountsAreIndependent() {
        for (int i = 0; i < 5; i++) service.recordFailure("a@b.com");
        assertTrue(service.isBlocked("a@b.com"));
        assertFalse(service.isBlocked("other@b.com"));
    }

    @Test
    void failuresOutsideTheWindowDoNotCount() {
        for (int i = 0; i < 4; i++) service.recordFailure("a@b.com");
        clock.advance(Duration.ofMinutes(11));
        service.recordFailure("a@b.com");
        assertFalse(service.isBlocked("a@b.com"));
    }

    @Test
    void theTrackingMapStaysBounded() {
        for (int i = 0; i < 25_000; i++) service.recordFailure("user" + i + "@x.com");
        clock.advance(Duration.ofMinutes(11));
        service.recordFailure("one-more@x.com");
        assertTrue(service.trackedKeys() < 10_001);
    }
}
