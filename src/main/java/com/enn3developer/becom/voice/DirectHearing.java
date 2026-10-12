package com.enn3developer.becom.voice;

import com.enn3developer.gtnhvoice.api.server.PlayerSnapshot;

/**
 * D17: a listener close enough to hear the speaker directly gets nothing from BeCom, so nobody hears a voice twice.
 * The range is the configured one, capped by GTNH Voice's own proximity range.
 */
public final class DirectHearing {

    private DirectHearing() {}

    /**
     * The range to skip within. VOICE.md V1: GTNH Voice can't tell an addon its effective proximity range yet, so the
     * configured range is used as if voice's own were at least that large.
     */
    public static double range(double configured) {
        // VOICE.md V1
        return configured;
    }

    /** Whether {@code listener} hears {@code speaker} directly, measured the way GTNH Voice measures it. */
    public static boolean hears(PlayerSnapshot speaker, PlayerSnapshot listener, double range) {
        if (speaker == null || listener == null) return false;
        if (speaker.getDimensionId() != listener.getDimensionId()) return false;
        return speaker.distanceTo(listener) <= range;
    }
}
