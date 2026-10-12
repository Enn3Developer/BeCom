package com.enn3developer.becom.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

class DropoutModelTest {

    private static final double FRAME_MILLIS = 20;
    private final DropoutModel model = Tunables.defaults()
        .dropoutModel(FRAME_MILLIS);

    @Test
    void lossFallsFromThirtyPercentAtSixToNothingAtTwelve() {
        assertEquals(0.30, model.lossRate(6), 1e-12);
        assertEquals(0.15, model.lossRate(9), 1e-12);
        assertEquals(0.0, model.lossRate(12), 1e-12);
        assertEquals(0.0, model.lossRate(30), 1e-12);
        // Below Low the link is on its way to dropping and keeps losing at the peak rate.
        assertEquals(0.30, model.lossRate(2), 1e-12);
    }

    @Test
    void meanBurstOfSixtyMillisecondsIsThreeFrames() {
        assertEquals(1.0 / 3, model.leaveBadChance(), 1e-12);
    }

    @Test
    void cleanLinkNeverDrops() {
        DropoutModel.Chain chain = new DropoutModel.Chain();
        double enter = model.enterBadChance(model.lossRate(12));
        assertEquals(0, enter);
        Random random = new Random(1);
        for (int i = 0; i < 10_000; i++) assertFalse(chain.drop(enter, model.leaveBadChance(), random.nextDouble()));
    }

    @Test
    void longRunLossAndBurstLengthMatchTheModel() {
        double target = model.lossRate(7.5);
        double enter = model.enterBadChance(target);
        double leave = model.leaveBadChance();
        DropoutModel.Chain chain = new DropoutModel.Chain();
        Random random = new Random(42);
        int frames = 2_000_000, lost = 0, bursts = 0;
        boolean previous = false;
        for (int i = 0; i < frames; i++) {
            boolean dropped = chain.drop(enter, leave, random.nextDouble());
            if (dropped) lost++;
            if (dropped && !previous) bursts++;
            previous = dropped;
        }
        assertEquals(target, (double) lost / frames, 0.005);
        double meanBurstFrames = (double) lost / bursts;
        assertEquals(3.0, meanBurstFrames, 0.1);
        assertTrue(bursts > 0);
    }

    @Test
    void videoFramesSampleTheSameChainAndLoseTheSameShare() {
        // A pose every 100 ms is a sample every fifth 20 ms frame: the share lost stays the long-run loss rate.
        double target = model.lossRate(7.5);
        double enter = model.enterBadChance(target);
        double leave = model.leaveBadChance();
        DropoutModel.Chain chain = new DropoutModel.Chain();
        Random random = new Random(7);
        int samples = 400_000, lost = 0;
        for (int i = 0; i < samples; i++) if (chain.dropAfter(5, enter, leave, random::nextDouble)) lost++;
        assertEquals(target, (double) lost / samples, 0.005);
        DropoutModel.Chain clean = new DropoutModel.Chain();
        assertFalse(clean.dropAfter(5, 0, leave, random::nextDouble));
        assertFalse(clean.dropAfter(0, 1, leave, random::nextDouble), "no frames run, nothing changes");
    }
}
