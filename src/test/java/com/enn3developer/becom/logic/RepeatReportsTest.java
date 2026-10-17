package com.enn3developer.becom.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** A quest task is reported again after a while, so one done while its quest was locked isn't lost. */
class RepeatReportsTest {

    // A monotonic clock, far from zero as on a real machine.
    private static final long T0 = 9_876_543_210L;

    @Test
    void aKeyIsReportedOncePerWindow() {
        RepeatReports<String> reports = new RepeatReports<>(60_000);
        assertTrue(reports.due("alice", T0));
        assertFalse(reports.due("alice", T0 + 1));
        assertFalse(reports.due("alice", T0 + 59_999));
        assertTrue(reports.due("bob", T0 + 1), "each key has its own window");
        assertTrue(reports.due("alice", T0 + 60_000));
        assertFalse(reports.due("alice", T0 + 60_001));
    }

    @Test
    void sweepingForgetsOnlyKeysWhoseWindowPassed() {
        RepeatReports<String> reports = new RepeatReports<>(60_000);
        reports.due("alice", T0);
        reports.due("bob", T0 + 30_000);
        reports.sweep(T0 + 60_000);
        assertEquals(1, reports.tracked());
        assertFalse(reports.due("bob", T0 + 60_000));
    }
}
