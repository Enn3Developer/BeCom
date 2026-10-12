package com.enn3developer.becom.logic.video;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.enn3developer.becom.logic.SignalLevel;
import com.enn3developer.becom.logic.Tunables;

/** DESIGN.md 10.1's table and 17's defaults: what each level allows each camera. */
class VideoQualityTest {

    private final Tunables t = Tunables.defaults();

    @Test
    void lowFreezesAndMediumMovesWithoutARearCamera() {
        assertFalse(
            VideoQuality.of(t, SignalLevel.LOW)
                .frontLive());
        VideoQuality medium = VideoQuality.of(t, SignalLevel.MEDIUM);
        assertEquals(48, medium.frontHeight());
        assertEquals(5, medium.frontFps());
        assertFalse(medium.rear());
    }

    @Test
    void highAndMaxCarryTheRearCamera() {
        VideoQuality high = VideoQuality.of(t, SignalLevel.HIGH);
        assertEquals(new VideoQuality(96, 12, 128, 72, 2), high);
        assertEquals(500, high.rearGapMillis());
        VideoQuality max = VideoQuality.of(t, SignalLevel.MAX);
        assertEquals(new VideoQuality(192, 0, 192, 108, 5), max);
        assertTrue(max.rear());
        assertEquals(200, max.rearGapMillis());
    }
}
