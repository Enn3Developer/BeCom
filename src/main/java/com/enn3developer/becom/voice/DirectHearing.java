package com.enn3developer.becom.voice;

import com.enn3developer.gtnhvoice.api.server.PlayerSnapshot;

/**
 * A listener close enough to hear the speaker directly gets nothing from BeCom, so nobody hears a voice twice.
 * The range is the configured one, capped by GTNH Voice's own proximity range.
 */
public final class DirectHearing {

    private DirectHearing() {}

    /**
     * The range to skip within: the configured range, capped by {@code proximity}, the range GTNH Voice's proximity
     * group cuts off at ({@code GtnhVoiceApi.proximityRange()}). Past that, nobody hears the speaker directly, so BeCom
     * carries the voice to them.
     */
    public static double range(double configured, double proximity) {
        return Math.min(configured, proximity);
    }

    /** Whether {@code listener} hears {@code speaker} directly, measured the way GTNH Voice measures it. */
    public static boolean hears(PlayerSnapshot speaker, PlayerSnapshot listener, double range) {
        if (speaker == null || listener == null) return false;
        if (speaker.getDimensionId() != listener.getDimensionId()) return false;
        return speaker.distanceTo(listener) <= range;
    }
}
