package com.enn3developer.becom.logic.radio;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.enn3developer.becom.logic.SignalLevel;
import com.enn3developer.becom.logic.Tunables;

/** A radio link's level: hysteresis, the Medium cap, and the drop after 3 s below Low. */
class RadioLinkLevelTest {

    private final Tunables t = Tunables.defaults();

    private RadioLinkLevel link() {
        return new RadioLinkLevel(t.levelScale(), SignalLevel.MEDIUM, (long) (t.dropAfterSeconds() * 1000));
    }

    @Test
    void entersAtTheThresholdAndStopsAtMedium() {
        RadioLinkLevel link = link();
        assertEquals(SignalLevel.NONE, link.update(5.9, 0));
        assertEquals(SignalLevel.LOW, link.update(6.0, 1000));
        assertEquals(SignalLevel.MEDIUM, link.update(30, 2000));
    }

    @Test
    void aDipBelowLowHoldsForTheGraceThenDrops() {
        RadioLinkLevel link = link();
        link.update(8, 0);
        // 5 dB is under Low's 6 but above the 4 hysteresis leaves it at: Low holds while the grace lasts.
        assertEquals(SignalLevel.LOW, link.update(5, 1000));
        assertEquals(SignalLevel.LOW, link.update(5, 4000));
        // More than 3 s below Low: it drops (4.2), though 5 dB is inside the hysteresis.
        assertEquals(SignalLevel.NONE, link.update(5, 4001));
        // And stays down until it reaches Low's own threshold again.
        assertEquals(SignalLevel.NONE, link.update(5.9, 5000));
        assertEquals(SignalLevel.LOW, link.update(6, 6000));
    }

    @Test
    void climbingBackAboveLowResetsTheGrace() {
        RadioLinkLevel link = link();
        link.update(8, 0);
        link.update(5, 1000);
        link.update(7, 3000);
        assertEquals(SignalLevel.LOW, link.update(5, 5000));
        assertEquals(SignalLevel.LOW, link.update(5, 8000));
        assertEquals(SignalLevel.NONE, link.update(5, 8001));
    }

    @Test
    void belowTheHysteresisItDropsAtOnce() {
        RadioLinkLevel link = link();
        link.update(8, 0);
        assertEquals(SignalLevel.NONE, link.update(3.9, 1000));
    }
}
