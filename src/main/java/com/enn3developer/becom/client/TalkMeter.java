package com.enn3developer.becom.client;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.enn3developer.gtnhvoice.api.client.IPcmChain;
import com.enn3developer.gtnhvoice.api.client.IPlaybackFrame;
import com.enn3developer.gtnhvoice.api.client.VoiceFormat;

/**
 * How loud each person is talking, for the front camera's talking animation: a stage at the
 * head of BeCom's playback chain, one instance per source, that measures every frame's loudness and passes every
 * sample through unchanged. It sees every voice this player hears, whichever group brings it, so someone standing
 * close by still talks on screen. Frames are measured on GTNH Voice's decode threads, read on the render thread.
 */
public final class TalkMeter implements IPcmChain.Stage {

    // A voice frame's RMS, against the loudness of normal speech, scaled to 0..1 on the way out.
    private static final double SPEECH_RMS = 0.12;
    // How long a measurement stands for: about two voice frames, so a gap between words reads as quiet.
    private static final long HOLD_MILLIS = 60;

    private record Level(float loudness, long at) {}

    private static final Map<UUID, Level> LEVELS = new ConcurrentHashMap<>();

    private UUID source;
    private double sum;
    private int count;

    @Override
    public void beginFrame(IPlaybackFrame frame) {
        flush();
        source = frame.sourceId();
    }

    @Override
    public double step(double sample) {
        sum += sample * sample;
        // Measured as each frame ends, not when the next one begins: that could be seconds later, after a pause.
        if (++count >= VoiceFormat.FRAME_SAMPLES) flush();
        return sample;
    }

    private void flush() {
        if (source != null && count > 0) {
            double rms = Math.sqrt(sum / count);
            LEVELS.put(source, new Level((float) Math.min(1, rms / SPEECH_RMS), System.currentTimeMillis()));
        }
        sum = 0;
        count = 0;
    }

    /** How loud {@code source} talks now, 0 to 1; 0 once they've been quiet a moment. */
    public static float loudness(UUID source) {
        Level level = LEVELS.get(source);
        if (level == null) return 0;
        if (System.currentTimeMillis() - level.at() > HOLD_MILLIS) {
            LEVELS.remove(source, level);
            return 0;
        }
        return level.loudness();
    }

    public static void clear() {
        LEVELS.clear();
    }
}
