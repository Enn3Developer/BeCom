package com.enn3developer.becom.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.enn3developer.becom.logic.Tunables;
import com.enn3developer.gtnhvoice.api.client.IPlaybackFrame;
import com.enn3developer.gtnhvoice.api.client.VoiceFormat;

/**
 * DESIGN.md 13.5: a walkie's or loudspeaker's frames fade to silence at its range, each over its own range, and nothing
 * else changes.
 */
class DistanceFadeTest {

    private static final UUID SPEAKER = UUID.randomUUID();
    private static final long FRAME_NANOS = 20_000_000L;
    // The listener's feet; the server's defaults carry a walkie 6 blocks and either loudspeaker 16.
    private static final DistanceFade.Listener AT = new DistanceFade.Listener(100, 64, 0, 6, 16, 16);

    private long now;

    private record Frame(String groupName, boolean positional, double x, double y, double z) implements IPlaybackFrame {

        @Override
        public UUID sourceId() {
            return SPEAKER;
        }

        @Override
        public String groupDisplayName() {
            return groupName;
        }
    }

    private static Frame radio(double x, double y, double z) {
        return new Frame("becom:radio.3", true, x, y, z);
    }

    @AfterEach
    void forget() {
        DistanceFade.forget();
    }

    @Test
    void aWalkieFadesOverItsOwnRange() {
        // Walkies play from their holder's feet, never a block's middle.
        assertEquals(1, DistanceFade.gain(radio(100, 64, 0), AT), 1e-12);
        assertEquals(0.75, DistanceFade.gain(radio(103, 64, 0), AT), 1e-12);
        assertEquals(0, DistanceFade.gain(radio(106, 64, 0), AT), 1e-12);
        assertEquals(0, DistanceFade.gain(radio(100, 64, 7), AT), 1e-12);
    }

    @Test
    void aTransceiverFadesOverItsLoudspeakersRange() {
        // From its block's middle, about 7.5 blocks from the listener's feet, where a walkie would be silent.
        assertEquals(1 - 56.75 / 256, DistanceFade.gain(radio(107.5, 64.5, 0.5), AT), 1e-12);
        assertEquals(0, DistanceFade.gain(radio(116.5, 64.5, 0.5), AT), 1e-12);
    }

    @Test
    void aVideophoneFadesOverItsLoudspeakersRange() {
        DistanceFade.Listener videophoneAt12 = new DistanceFade.Listener(100, 64, 0, 6, 16, 12);
        assertEquals(0.75, DistanceFade.gain(new Frame("becom:call", true, 106, 64, 0), videophoneAt12), 1e-12);
        assertEquals(0, DistanceFade.gain(new Frame("becom:call", true, 112, 64, 0), videophoneAt12), 1e-12);
    }

    @Test
    void everythingElsePlaysAsItCame() {
        assertEquals(1, DistanceFade.gain(new Frame("becom:radio.3", false, Double.NaN, Double.NaN, Double.NaN), AT));
        assertEquals(1, DistanceFade.gain(new Frame("becom:call", false, Double.NaN, Double.NaN, Double.NaN), AT));
        assertEquals(1, DistanceFade.gain(new Frame("local", true, 140, 64, 0), AT));
        assertEquals(1, DistanceFade.gain(radio(140, 64, 0), null), "no player in a world yet");
        assertEquals(1, DistanceFade.gain(new Frame("local", true, Double.NaN, Double.NaN, Double.NaN), AT));
    }

    @Test
    void aBeComFrameFromAPositionThatIsntANumberIsSilent() {
        // It used to play at full gain.
        assertEquals(0, DistanceFade.gain(radio(Double.NaN, Double.NaN, Double.NaN), AT));
        assertEquals(0, DistanceFade.gain(radio(100, Double.NaN, 0), AT));
        assertEquals(0, DistanceFade.gain(radio(Double.POSITIVE_INFINITY, 64, 0), AT));
        assertEquals(0, DistanceFade.gain(new Frame("becom:call", true, Double.NaN, 64.5, 0.5), AT));
        // Nor is anything in range of a player whose own position isn't a number.
        DistanceFade.Listener lost = new DistanceFade.Listener(Double.NaN, 64, 0, 6, 16, 16);
        assertEquals(0, DistanceFade.gain(radio(100, 64, 0), lost));
        assertEquals(1, DistanceFade.gain(new Frame("becom:radio.3", false, Double.NaN, Double.NaN, Double.NaN), lost));
    }

    @Test
    void theGainMovesAcrossAFrameNotBetweenThem() {
        DistanceFade.listen(100, 64, 0, Tunables.defaults());
        DistanceFade stage = new DistanceFade(() -> now);
        // The first frame plays at its own gain throughout.
        assertEquals(List.of(0.75, 0.75), play(stage, radio(103, 64, 0)));
        // The listener walks a block closer: the next frame starts where the last ended and ends at its own gain.
        DistanceFade.listen(101, 64, 0, Tunables.defaults());
        now += FRAME_NANOS;
        List<Double> closer = play(stage, radio(103, 64, 0));
        double expected = 1 - 4.0 / 36;
        assertEquals(0.75, closer.get(0), 1e-12);
        assertEquals(expected, closer.get(1), (expected - 0.75) / VoiceFormat.FRAME_SAMPLES * 1.01);
        assertEquals(expected, stage.step(1), 1e-12, "and holds it if the frame runs long");
    }

    @Test
    void afterAPauseTheFirstFrameStartsAtItsOwnGain() {
        DistanceFade.listen(100, 64, 0, Tunables.defaults());
        DistanceFade stage = new DistanceFade(() -> now);
        play(stage, radio(103, 64, 0));
        // A second later the speaker talks again, the listener now a block closer: no ramp up from the old gain.
        DistanceFade.listen(101, 64, 0, Tunables.defaults());
        now += 50 * FRAME_NANOS;
        List<Double> again = play(stage, radio(103, 64, 0));
        assertEquals(1 - 4.0 / 36, again.get(0), 1e-12);
        assertEquals(1 - 4.0 / 36, again.get(1), 1e-12);
    }

    /** One frame through the stage: the gain of its first sample and its last. */
    private static List<Double> play(DistanceFade stage, IPlaybackFrame frame) {
        stage.beginFrame(frame);
        double first = stage.step(1);
        double last = first;
        for (int i = 1; i < VoiceFormat.FRAME_SAMPLES; i++) last = stage.step(1);
        return List.of(first, last);
    }
}
