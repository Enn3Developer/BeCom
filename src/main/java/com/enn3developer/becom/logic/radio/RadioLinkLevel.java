package com.enn3developer.becom.logic.radio;

import com.enn3developer.becom.logic.DropTimer;
import com.enn3developer.becom.logic.LevelScale;
import com.enn3developer.becom.logic.SignalLevel;

/**
 * One radio link's level from one speaker to one receiving device, evaluated once a second: it
 * enters a level at its threshold and leaves it 2 dB below, stops at the radios' cap, and like any link drops
 * once it has stayed below Low for longer than the drop grace, the same for wires and radio. A dropped link comes back
 * only at Low's own threshold.
 */
public final class RadioLinkLevel {

    private final LevelScale levels;
    private final SignalLevel cap;
    private final DropTimer drop;
    private SignalLevel level = SignalLevel.NONE;

    public RadioLinkLevel(LevelScale levels, SignalLevel cap, long dropGraceMillis) {
        this.levels = levels;
        this.cap = cap;
        this.drop = new DropTimer(levels.threshold(SignalLevel.LOW), dropGraceMillis);
    }

    /** Feeds one evaluation at {@code nowMillis}, on a monotonic clock, and returns the link's level after it. */
    public SignalLevel update(double snr, long nowMillis) {
        if (drop.update(snr, nowMillis)) {
            level = SignalLevel.NONE;
        } else {
            level = SignalLevel.min(levels.next(level, snr), cap);
        }
        return level;
    }

    public SignalLevel level() {
        return level;
    }
}
