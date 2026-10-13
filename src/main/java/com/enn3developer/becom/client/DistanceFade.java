package com.enn3developer.becom.client;

import java.util.function.LongSupplier;

import com.enn3developer.becom.logic.Positions;
import com.enn3developer.becom.logic.SpeakerFade;
import com.enn3developer.becom.logic.Tunables;
import com.enn3developer.becom.voice.CallGroup;
import com.enn3developer.becom.voice.RadioGroup;
import com.enn3developer.gtnhvoice.api.client.IPcmChain;
import com.enn3developer.gtnhvoice.api.client.IPlaybackFrame;
import com.enn3developer.gtnhvoice.api.client.VoiceFormat;

/**
 * Fades BeCom's positional frames to silence at their speaker's range (DESIGN.md 13.5): the last stage of BeCom's
 * playback chain, one instance per source. A walkie's frames fade over the walkie speaker's range (D21), a
 * Transceiver's over its loudspeaker's and a Videophone's over its own, all the server's values. Every other frame
 * passes unchanged.
 * <p>
 * Frames play on GTNH Voice's decode threads, which never read the world: they read where this player stands from
 * {@link #listen}, which the client tick calls. The gain moves from one frame's value to the next across the frame,
 * so a player walking away hears a fade, not steps.
 */
public final class DistanceFade implements IPcmChain.Stage {

    // Frames come every 20 ms while someone talks. After a longer gap the gain the last one ended at is stale.
    private static final long PAUSE_NANOS = 100_000_000L;

    /** Where this player's feet are, as the server measures a speaker's range, and the server's ranges. */
    record Listener(double x, double y, double z, double walkie, double loudspeaker, double videophone) {}

    private static volatile Listener listener;

    private final LongSupplier clock;
    // The gain at the start of the frame and at its end, how far into the frame this is, and when it began.
    private double from = Double.NaN;
    private double to;
    private int sample;
    private long began;

    public DistanceFade() {
        this(System::nanoTime);
    }

    DistanceFade(LongSupplier clock) {
        this.clock = clock;
    }

    /** Client thread, every tick: this player's feet and the server's tunables, or nothing between worlds. */
    public static void listen(double x, double feetY, double z, Tunables server) {
        listener = new Listener(
            x,
            feetY,
            z,
            server.walkieSpeakerRange(),
            server.loudspeakerRange(),
            server.videophoneLoudspeaker());
    }

    public static void forget() {
        listener = null;
    }

    @Override
    public void beginFrame(IPlaybackFrame frame) {
        double gain = gain(frame, listener);
        long now = clock.getAsLong();
        from = Double.isNaN(from) || now - began > PAUSE_NANOS ? gain : to;
        to = gain;
        sample = 0;
        began = now;
    }

    @Override
    public double step(double x) {
        double gain = from + (to - from) * sample / VoiceFormat.FRAME_SAMPLES;
        if (sample < VoiceFormat.FRAME_SAMPLES) sample++;
        return x * gain;
    }

    /**
     * The gain a frame ends at: faded by distance for a positional BeCom frame, 1 for anything else. A BeCom frame from
     * a
     * position that isn't finite, or heard where this player's isn't, is out of every range: silent.
     */
    static double gain(IPlaybackFrame frame, Listener at) {
        if (at == null || !frame.positional()) return 1;
        double range = range(frame, at);
        if (Double.isNaN(range)) return 1;
        if (!Positions.finite(frame.x(), frame.y(), frame.z()) || !Positions.finite(at.x(), at.y(), at.z())) return 0;
        double dx = frame.x() - at.x();
        double dy = frame.y() - at.y();
        double dz = frame.z() - at.z();
        return SpeakerFade.gain(Math.sqrt(dx * dx + dy * dy + dz * dz), range);
    }

    /** How far the speaker a positional frame plays from carries, or NaN for a group that isn't BeCom's. */
    private static double range(IPlaybackFrame frame, Listener at) {
        String group = frame.groupName();
        if (CallGroup.NAME.equals(group)) return at.videophone();
        if (group == null || !group.startsWith(RadioGroup.PREFIX)) return Double.NaN;
        return SpeakerFade.blockMiddle(frame.x(), frame.y(), frame.z()) ? at.loudspeaker() : at.walkie();
    }
}
