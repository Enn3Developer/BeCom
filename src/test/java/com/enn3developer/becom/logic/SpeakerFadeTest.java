package com.enn3developer.becom.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

/** How a walkie or loudspeaker fades with distance, and how a client tells the two apart. */
class SpeakerFadeTest {

    @Test
    void fullUpCloseAndSilentFromTheRangeOut() {
        assertEquals(1, SpeakerFade.gain(0, 6), 1e-12);
        assertEquals(0.75, SpeakerFade.gain(3, 6), 1e-12);
        assertEquals(0, SpeakerFade.gain(6, 6), 1e-12);
        assertEquals(0, SpeakerFade.gain(7, 6), 1e-12);
        assertEquals(0.75, SpeakerFade.gain(8, 16), 1e-12);
        // A speaker configured to carry nowhere is silent everywhere.
        assertEquals(0, SpeakerFade.gain(0, 0), 1e-12);
        assertEquals(0, SpeakerFade.gain(Double.NaN, 6), 1e-12);
    }

    @Test
    void aSpeakersOtherSoundsFadeAsItsVoiceDoes() {
        // The squelch tail at 5.5 blocks from a walkie: as faint, against its own close-up volume, as the voice.
        assertEquals(0.5f * (float) SpeakerFade.gain(5.5, 6), SpeakerFade.volume(0.5f, 5.5, 6), 1e-6);
        assertEquals(0.5f, SpeakerFade.volume(0.5f, 0, 6), 1e-6);
        assertEquals(0, SpeakerFade.volume(0.5f, 6, 6), 1e-6);
    }

    @Test
    void quieterTheFurtherAway() {
        double last = 1;
        for (double d = 0.25; d <= 6; d += 0.25) {
            double gain = SpeakerFade.gain(d, 6);
            assertTrue(gain < last, "at " + d);
            last = gain;
        }
    }

    @Test
    void loudspeakersPlayFromTheMiddleOfTheirBlock() {
        assertTrue(SpeakerFade.blockMiddle(200.5, 64.5, 0.5));
        assertTrue(SpeakerFade.blockMiddle(-0.5, 0.5, -1_000_000.5));
        assertFalse(SpeakerFade.blockMiddle(200.5, 64, 0.5));
        assertFalse(SpeakerFade.blockMiddle(100, 64, 0));
    }

    @Test
    void aWalkieNeverPlaysFromABlocksMiddle() {
        // A holder standing on a slab at a block's middle moves by the smallest step there is.
        double y = SpeakerFade.walkieY(100.5, 64.5, 0.5);
        assertNotEquals(64.5, y);
        assertEquals(64.5, y, 1e-9);
        assertFalse(SpeakerFade.blockMiddle(100.5, y, 0.5));
        assertFalse(SpeakerFade.blockMiddle(-0.5, SpeakerFade.walkieY(-0.5, -0.5, -0.5), -0.5));
        // Anywhere else, the holder's own height.
        assertEquals(64.5, SpeakerFade.walkieY(100.25, 64.5, 0.5));
        assertEquals(64, SpeakerFade.walkieY(100.5, 64, 0.5));
        Random random = new Random(7);
        for (int i = 0; i < 10_000; i++) {
            // Half the holders exactly at a block's middle, within the world's border and below the void.
            double x = random.nextInt(60_000_000) - 30_000_000 + (random.nextBoolean() ? 0.5 : random.nextDouble());
            double holderY = random.nextInt(320) - 64 + (random.nextBoolean() ? 0.5 : random.nextDouble());
            double z = random.nextInt(60_000_000) - 30_000_000 + 0.5;
            assertFalse(SpeakerFade.blockMiddle(x, SpeakerFade.walkieY(x, holderY, z), z), x + " " + holderY + " " + z);
        }
    }
}
