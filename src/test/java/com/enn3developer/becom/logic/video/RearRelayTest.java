package com.enn3developer.becom.logic.video;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.ToDoubleBiFunction;

import org.junit.jupiter.api.Test;

import com.enn3developer.becom.logic.SignalLevel;
import com.enn3developer.becom.logic.Tunables;
import com.enn3developer.becom.logic.call.Call;
import com.enn3developer.becom.logic.call.CallBook;
import com.enn3developer.becom.logic.phone.PhoneKind;

/** DESIGN.md 10.1, 14: rear pictures go on to the one viewer, held to the client caps and the viewer's level. */
class RearRelayTest {

    private static final int MOBILE_A = 111_111;
    private static final int MOBILE_B = 222_222;
    private static final int ME_A = 333_333;
    private static final int ME_B = 444_444;
    // A monotonic clock in milliseconds, far from zero as it is on a real machine.
    private static final long T0 = 1_234_567_890L;
    private static final ToDoubleBiFunction<UUID, UUID> CLEAN = (sender, viewer) -> 40;

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final Map<Integer, PhoneKind> kinds = Map
        .of(MOBILE_A, PhoneKind.MOBILE, MOBILE_B, PhoneKind.MOBILE, ME_A, PhoneKind.ME_PHONE, ME_B, PhoneKind.ME_PHONE);
    private final Map<UUID, VideoPlan.Cameras> cameras = new HashMap<>();

    private final CallBook book = new CallBook(
        Tunables.defaults()
            .levelScale(),
        60,
        30_000,
        3_000,
        10_000,
        8,
        30_000,
        new CallBook.Listener() {

            @Override
            public void ringing(Call call, Call.Leg leg) {}

            @Override
            public void connected(Call call, Call.Leg answered) {}

            @Override
            public void legLeft(Call call, Call.Leg leg, Set<UUID> players, CallBook.EndReason reason) {}

            @Override
            public void ended(Call call, CallBook.EndReason reason) {}

            @Override
            public void levelChanged(Call call, UUID speaker, UUID listener, SignalLevel level) {}

            @Override
            public void roomChanged(Call call, Call.Leg leg, Set<UUID> joined, Set<UUID> left) {}
        });

    private final CallBook.Quality quality = new CallBook.Quality() {

        @Override
        public double snr(Call call, Call.Leg speaker, Call.Leg listener) {
            return 40;
        }

        @Override
        public SignalLevel cap(Call call, Call.Leg speaker, Call.Leg listener) {
            return SignalLevel.min(
                kinds.get(speaker.phone())
                    .cap(),
                kinds.get(listener.phone())
                    .cap());
        }
    };

    /** Alice on {@code from} calls Bob on {@code to}, and switches her rear camera on. */
    private VideoPlan call(int from, int to) {
        book.dial(alice, from, to, 0, CallBook.Check.OK);
        book.answer(bob, to, 100);
        Call call = book.callOf(alice);
        book.evaluate(call, 1000, quality);
        cameras.put(alice, new VideoPlan.Cameras(true, true));
        return plan();
    }

    private VideoPlan plan() {
        Call call = book.callOf(alice);
        return call == null ? VideoPlan.EMPTY
            : VideoPlan.of(call, kinds::get, player -> cameras.getOrDefault(player, VideoPlan.Cameras.START));
    }

    @Test
    void aPictureReachesTheOtherSideOfAOneToOneCall() {
        VideoPlan plan = call(MOBILE_A, MOBILE_B);
        RearRelay relay = new RearRelay(Tunables.defaults(), () -> 0.5);
        assertEquals(SignalLevel.HIGH, plan.rearSendOf(alice));
        assertEquals(List.of(bob), relay.relay(alice, 100, T0, plan, CLEAN));
        assertEquals(1, relay.delivered(alice, bob));
        assertEquals(List.of(), relay.relay(bob, 100, T0, plan, CLEAN), "Bob's rear camera is off");
    }

    @Test
    void anEmptyPictureOrOneOverTheByteCapGoesNowhere() {
        VideoPlan plan = call(MOBILE_A, MOBILE_B);
        Tunables t = Tunables.defaults();
        RearRelay relay = new RearRelay(t, () -> 0.5);
        assertEquals(List.of(), relay.relay(alice, t.rearMaxBytes() + 1, T0, plan, CLEAN));
        assertEquals(List.of(), relay.relay(alice, 0, T0, plan, CLEAN));
        assertEquals(List.of(bob), relay.relay(alice, t.rearMaxBytes(), T0, plan, CLEAN));
    }

    /**
     * Ten seconds of a client sending as fast as the per-client cap lets it: the viewer gets their level's rate, give
     * or
     * take the two pictures the allowance holds.
     */
    private static void assertLevelRate(VideoPlan plan, UUID sender, UUID viewer, int fps) {
        Tunables t = Tunables.defaults();
        RearRelay relay = new RearRelay(t, () -> 0.5);
        long interval = 1000L / t.rearMaxFps() + 1;
        for (long at = 0; at < 10_000; at += interval) relay.relay(sender, 100, T0 + at, plan, CLEAN);
        int delivered = relay.delivered(sender, viewer);
        assertTrue(delivered >= 10 * fps - 2 && delivered <= 10 * fps + 2, delivered + " in 10 s at " + fps + " fps");
    }

    @Test
    void aViewerGetsPicturesNoFasterThanTheirLevelAllows() {
        assertLevelRate(
            call(MOBILE_A, MOBILE_B),
            alice,
            bob,
            Tunables.defaults()
                .rearFpsHigh());
    }

    @Test
    void atMaxTheLevelsRateHolds() {
        VideoPlan plan = call(ME_A, ME_B);
        assertEquals(SignalLevel.MAX, plan.rearSendOf(alice));
        assertLevelRate(
            plan,
            alice,
            bob,
            Tunables.defaults()
                .rearFpsMax());
    }

    @Test
    void aPictureALittleEarlyAfterALateOneStillGoesOn() {
        VideoPlan plan = call(MOBILE_A, MOBILE_B);
        Tunables t = Tunables.defaults();
        RearRelay relay = new RearRelay(t, () -> 0.5);
        long gap = 1000L / t.rearFpsHigh();
        // Sent on time, reaching the server a tick late, then a tick early.
        relay.relay(alice, 100, T0, plan, CLEAN);
        relay.relay(alice, 100, T0 + gap + 50, plan, CLEAN);
        relay.relay(alice, 100, T0 + 2 * gap - 50, plan, CLEAN);
        assertEquals(3, relay.delivered(alice, bob));
    }

    @Test
    void theClientsOwnRateCapHoldsAtAnyLevel() {
        // Every frame at Max: only the per-client cap is left.
        Tunables t = Tunables.fromMap(Map.of("rearFpsMax", 0));
        VideoPlan plan = call(ME_A, ME_B);
        RearRelay relay = new RearRelay(t, () -> 0.5);
        for (int i = 0; i < 20; i++) relay.relay(alice, 100, T0 + i * 10L, plan, CLEAN);
        assertEquals(t.rearMaxFps(), relay.delivered(alice, bob));
    }

    @Test
    void aLinkAtFullLossDropsPictures() {
        VideoPlan plan = call(MOBILE_A, MOBILE_B);
        Tunables t = Tunables.defaults();
        double fullLoss = t.levelScale()
            .threshold(SignalLevel.LOW);
        RearRelay relay = new RearRelay(t, () -> 0.0);
        for (int i = 0; i < 10; i++) relay.relay(alice, 100, T0 + i * 1000L, plan, (sender, viewer) -> fullLoss);
        assertEquals(0, relay.delivered(alice, bob), "a roll of 0 starts a burst whenever losses are possible");
        RearRelay clean = new RearRelay(t, () -> 0.0);
        for (int i = 0; i < 10; i++) clean.relay(alice, 100, T0 + i * 1000L, plan, CLEAN);
        assertEquals(10, clean.delivered(alice, bob), "and never on a clean link");
    }

    @Test
    void switchingTheRearCameraOffForgetsTheViewer() {
        VideoPlan plan = call(MOBILE_A, MOBILE_B);
        RearRelay relay = new RearRelay(Tunables.defaults(), () -> 0.5);
        relay.relay(alice, 100, T0, plan, CLEAN);
        cameras.put(alice, new VideoPlan.Cameras(true, false));
        VideoPlan off = plan();
        relay.keep(off);
        assertEquals(0, relay.delivered(alice, bob));
        assertEquals(List.of(), relay.relay(alice, 100, T0 + 5000, off, CLEAN));
    }
}
