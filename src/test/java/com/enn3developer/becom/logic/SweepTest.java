package com.enn3developer.becom.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Periodic checks run a slice per tick, inside their budget, and always finish. */
class SweepTest {

    private long now;

    @Test
    void stopsWhenTheBudgetIsSpent() {
        Sweep<Integer> sweep = new Sweep<>();
        sweep.add(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10));
        List<Integer> seen = new ArrayList<>();
        // Each item costs 300 us; a 1 ms budget fits four (the fourth crosses the line and ends the slice).
        int done = sweep.run(i -> {
            seen.add(i);
            now += 300_000;
        }, () -> now, 1_000_000);
        assertEquals(4, done);
        assertEquals(List.of(1, 2, 3, 4), seen);
        assertEquals(6, sweep.remaining());
    }

    @Test
    void alwaysTakesAtLeastOne() {
        Sweep<String> sweep = new Sweep<>();
        sweep.add(List.of("slow", "next"));
        assertEquals(1, sweep.run(s -> now += 50_000_000, () -> now, 1_000_000));
        assertEquals(1, sweep.remaining());
    }

    @Test
    void finishesEverythingOverEnoughTicks() {
        Sweep<Integer> sweep = new Sweep<>();
        List<Integer> items = new ArrayList<>();
        for (int i = 0; i < 2000; i++) items.add(i);
        sweep.add(items);
        int ticks = 0;
        while (!sweep.isEmpty()) {
            sweep.run(i -> now += 100_000, () -> now, 1_000_000);
            ticks++;
        }
        assertEquals(200, ticks);
        assertTrue(sweep.isEmpty());
    }
}
