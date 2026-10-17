package com.enn3developer.becom.logic;

/**
 * How a BeCom speaker gets quieter with distance: a walkie's, or a Transceiver's or Videophone's
 * loudspeaker. The server stops sending at the speaker's range, and voice's own falloff alone would still leave a
 * walkie at about -16 dB there, so each client fades the frame on top of it, to silence at the range.
 * <p>
 * A client tells a loudspeaker's frame from a walkie's by where it plays from: a loudspeaker from the exact middle of
 * its block, a walkie from its holder, whom the server moves off any block's middle ({@link #walkieY}).
 */
public final class SpeakerFade {

    private SpeakerFade() {}

    /**
     * The gain for a listener {@code distance} blocks from a speaker that carries {@code range} blocks: 1 up close,
     * down to 0 at the range and past it. The one place the curve lives, to be tuned by ear.
     */
    public static double gain(double distance, double range) {
        if (!(distance < range)) return 0;
        double share = distance / range;
        return 1 - share * share;
    }

    /**
     * The volume of another sound a speaker plays, such as the radio's squelch tail: {@code full} up close, and faded
     * as the voice is, so it never outlasts the voice toward the edge of the range.
     */
    public static float volume(float full, double distance, double range) {
        return (float) (full * gain(distance, range));
    }

    /** Whether a frame plays from the exact middle of a block, as a loudspeaker's do and a walkie's never do. */
    public static boolean blockMiddle(double x, double y, double z) {
        return middle(x) && middle(y) && middle(z);
    }

    /**
     * The height a walkie's frame plays from: its holder's, or, when the holder stands exactly at a block's middle, the
     * next double up, so a client never takes the walkie for a loudspeaker.
     */
    public static double walkieY(double x, double y, double z) {
        return blockMiddle(x, y, z) ? Math.nextUp(y) : y;
    }

    // Exact for any coordinate a world holds: a whole number plus a half is a double, and so is the sum.
    private static boolean middle(double v) {
        return v == Math.floor(v) + 0.5;
    }
}
