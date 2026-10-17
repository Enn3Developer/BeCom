package com.enn3developer.becom.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Random;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/** Once-a-second work, one tick of the second for each thing. */
class SlotsTest {

    @Test
    void everythingIsDueOnceInAnyTwentyTicks() {
        Random random = new Random(5);
        for (int i = 0; i < 1_000; i++) {
            UUID key = new UUID(random.nextLong(), random.nextLong());
            long start = random.nextInt(1_000_000);
            int due = 0;
            for (long tick = start; tick < start + Slots.TICKS_PER_SECOND; tick++) if (Slots.due(key, tick)) due++;
            assertEquals(1, due, key.toString());
        }
    }
}
