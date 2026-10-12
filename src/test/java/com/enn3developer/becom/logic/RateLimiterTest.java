package com.enn3developer.becom.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** DESIGN.md 12.3: at most 5 call attempts per minute per caller. */
class RateLimiterTest {

    @Test
    void fiveAttemptsAMinuteThenRefused() {
        RateLimiter<String> limiter = new RateLimiter<>(5, 60_000);
        for (int i = 0; i < 5; i++) assertTrue(limiter.tryAcquire("alice", i * 1000L));
        assertFalse(limiter.tryAcquire("alice", 10_000));
        // Others aren't affected.
        assertTrue(limiter.tryAcquire("bob", 10_000));
    }

    @Test
    void windowSlides() {
        RateLimiter<String> limiter = new RateLimiter<>(5, 60_000);
        for (int i = 0; i < 5; i++) assertTrue(limiter.tryAcquire("alice", i * 1000L));
        assertFalse(limiter.tryAcquire("alice", 59_999));
        // The first attempt leaves the window at 60 s.
        assertTrue(limiter.tryAcquire("alice", 60_000));
        assertFalse(limiter.tryAcquire("alice", 60_500));
    }

    @Test
    void refusedAttemptsDontCount() {
        RateLimiter<String> limiter = new RateLimiter<>(1, 60_000);
        assertTrue(limiter.tryAcquire("alice", 0));
        for (int i = 1; i < 100; i++) assertFalse(limiter.tryAcquire("alice", i * 100L));
        assertTrue(limiter.tryAcquire("alice", 60_000));
    }

    @Test
    void sweepForgetsQuietCallers() {
        RateLimiter<String> limiter = new RateLimiter<>(5, 60_000);
        limiter.tryAcquire("alice", 0);
        limiter.tryAcquire("bob", 30_000);
        limiter.sweep(61_000);
        assertEquals(1, limiter.trackedKeys());
        limiter.sweep(91_000);
        assertEquals(0, limiter.trackedKeys());
    }
}
