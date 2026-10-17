package com.enn3developer.becom.logic.call;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.enn3developer.becom.logic.SignalLevel;
import com.enn3developer.becom.logic.Tunables;

/** Call states, one call at a time, ringing, levels per direction, drops and session grace. */
class CallBookTest {

    private static final int PHONE_A = 111_111;
    private static final int PHONE_B = 222_222;
    private static final int PHONE_C = 333_333;

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final UUID carol = UUID.randomUUID();
    private final List<String> events = new ArrayList<>();
    private final Map<String, SignalLevel> levels = new HashMap<>();
    private double snr = 18;
    private SignalLevel cap = SignalLevel.LOW;

    private final CallBook book = new CallBook(
        Tunables.defaults()
            .levelScale(),
        5,
        30_000,
        3_000,
        10_000,
        4,
        30_000,
        new CallBook.Listener() {

            @Override
            public void ringing(Call call, Call.Leg leg) {
                events.add("ringing " + leg.phone());
            }

            @Override
            public void connected(Call call, Call.Leg answered) {
                events.add("connected");
            }

            @Override
            public void ended(Call call, CallBook.EndReason reason) {
                events.add("ended " + reason + (call.wasConnected() ? " after connecting" : " unanswered"));
            }

            @Override
            public void legLeft(Call call, Call.Leg leg, Set<UUID> players, CallBook.EndReason reason) {
                events.add("left " + leg.phone() + " " + reason);
            }

            @Override
            public void levelChanged(Call call, UUID speaker, UUID listener, SignalLevel level) {
                levels.put(speaker + ">" + listener, level);
            }

            @Override
            public void roomChanged(Call call, Call.Leg leg, Set<UUID> joined, Set<UUID> left) {
                events.add("room " + leg.phone() + " +" + joined.size() + " -" + left.size());
            }
        });

    private final CallBook.Quality quality = new CallBook.Quality() {

        @Override
        public double snr(Call call, Call.Leg speaker, Call.Leg listener) {
            return snr;
        }

        @Override
        public SignalLevel cap(Call call, Call.Leg speaker, Call.Leg listener) {
            return cap;
        }
    };

    private Call connect() {
        assertEquals(CallBook.DialResult.RINGING, book.dial(alice, PHONE_A, PHONE_B, 0, CallBook.Check.OK));
        assertTrue(book.answer(bob, PHONE_B, 1000));
        return book.callOf(alice);
    }

    @Test
    void dialRingsAndAnswerConnects() {
        Call call = connect();
        assertEquals(Call.State.CONNECTED, call.state());
        assertEquals(List.of(alice, bob), call.participants());
        assertEquals(call, book.callOf(bob));
        assertEquals(call, book.callOfPhone(PHONE_B));
        assertEquals(List.of("ringing " + PHONE_B, "connected"), events);
    }

    @Test
    void busyWhenTheOtherPhoneIsInACallOrTheWorldSaysSo() {
        connect();
        assertEquals(CallBook.DialResult.BUSY, book.dial(carol, PHONE_C, PHONE_B, 2000, CallBook.Check.OK));
        UUID dave = UUID.randomUUID();
        assertEquals(CallBook.DialResult.BUSY, book.dial(dave, 444_444, 555_555, 2000, CallBook.Check.BUSY));
        assertEquals(
            CallBook.DialResult.UNREACHABLE,
            book.dial(dave, 444_444, 555_555, 2000, CallBook.Check.UNREACHABLE));
    }

    @Test
    void oneCallAtATimeAndNoCallingYourself() {
        connect();
        assertEquals(CallBook.DialResult.INVALID, book.dial(alice, PHONE_A, PHONE_C, 2000, CallBook.Check.OK));
        assertEquals(CallBook.DialResult.INVALID, book.dial(carol, PHONE_C, PHONE_C, 2000, CallBook.Check.OK));
        // A phone already in a call can't dial out either.
        assertEquals(CallBook.DialResult.INVALID, book.dial(carol, PHONE_A, PHONE_C, 2000, CallBook.Check.OK));
    }

    @Test
    void rateLimitCountsBusyAndUnreachableAttempts() {
        for (int i = 0; i < 5; i++) {
            assertEquals(
                CallBook.DialResult.UNREACHABLE,
                book.dial(alice, PHONE_A, PHONE_B, i, CallBook.Check.UNREACHABLE));
        }
        assertEquals(CallBook.DialResult.RATE_LIMITED, book.dial(alice, PHONE_A, PHONE_B, 10, CallBook.Check.OK));
        assertEquals(CallBook.DialResult.RINGING, book.dial(alice, PHONE_A, PHONE_B, 60_000, CallBook.Check.OK));
    }

    /** The world check finds a path through the networks: a client flooding dials mustn't get one per attempt. */
    @Test
    void theWorldIsCheckedOnlyForAttemptsTheRateLimitLetsThrough() {
        int[] checks = new int[1];
        java.util.function.Supplier<CallBook.Check> check = () -> {
            checks[0]++;
            return CallBook.Check.UNREACHABLE;
        };
        for (int i = 0; i < 20; i++) book.dial(alice, PHONE_A, PHONE_B, i, check);
        assertEquals(5, checks[0]);
    }

    @Test
    void ringingGivesUpAfterThirtySecondsAsAMissedCall() {
        book.dial(alice, PHONE_A, PHONE_B, 0, CallBook.Check.OK);
        book.tick(29_999);
        assertEquals(
            Call.State.RINGING,
            book.callOf(alice)
                .state());
        book.tick(30_000);
        assertNull(book.callOf(alice));
        assertNull(book.callOfPhone(PHONE_B));
        assertEquals("ended NO_ANSWER unanswered", events.get(events.size() - 1));
    }

    @Test
    void hangingUpEndsTheCallForEveryone() {
        connect();
        assertTrue(book.hangUp(bob));
        assertNull(book.callOf(alice));
        assertNull(book.callOfPhone(PHONE_A));
        assertEquals("ended HUNG_UP after connecting", events.get(events.size() - 1));
        assertFalse(book.hangUp(bob));
    }

    @Test
    void rejectingOnlyWorksOnTheRingingEnd() {
        book.dial(alice, PHONE_A, PHONE_B, 0, CallBook.Check.OK);
        assertFalse(book.reject(PHONE_A));
        assertTrue(book.reject(PHONE_B));
        assertEquals("ended REJECTED unanswered", events.get(events.size() - 1));
    }

    @Test
    void answeringWhileInAnotherCallFails() {
        connect();
        assertEquals(CallBook.DialResult.RINGING, book.dial(carol, PHONE_C, 444_444, 0, CallBook.Check.OK));
        assertFalse(book.answer(alice, 444_444, 10));
    }

    @Test
    void levelsArePerDirectionCappedAndHysteretic() {
        Call call = connect();
        book.evaluate(2000, quality);
        // 18 dB is High, but a Telephone caps it at Low.
        assertEquals(SignalLevel.LOW, call.level(alice, bob));
        assertEquals(SignalLevel.LOW, call.level(bob, alice));
        assertEquals(SignalLevel.LOW, levels.get(alice + ">" + bob));
        cap = SignalLevel.MAX;
        snr = 12;
        book.evaluate(3000, quality);
        assertEquals(SignalLevel.MEDIUM, call.level(alice, bob));
        snr = 10.5;
        book.evaluate(4000, quality);
        assertEquals(SignalLevel.MEDIUM, call.level(alice, bob), "holds within 2 dB below the threshold");
        snr = 9.5;
        book.evaluate(5000, quality);
        assertEquals(SignalLevel.LOW, call.level(alice, bob));
    }

    @Test
    void levelChangesAreReportedOnlyWhenTheyChange() {
        connect();
        book.evaluate(2000, quality);
        levels.clear();
        book.evaluate(3000, quality);
        assertTrue(levels.isEmpty());
    }

    @Test
    void aLinkBelowLowForMoreThanThreeSecondsDrops() {
        Call call = connect();
        book.evaluate(2000, quality);
        snr = 5;
        book.evaluate(3000, quality);
        book.evaluate(6000, quality);
        assertEquals(Call.State.CONNECTED, call.state());
        book.evaluate(6001, quality);
        assertEquals(Call.State.ENDED, call.state());
        assertEquals("ended DROPPED after connecting", events.get(events.size() - 1));
    }

    @Test
    void aTimedOutSessionGetsTenSecondsToComeBack() {
        connect();
        book.sessionLost(alice, 5000);
        book.tick(14_999);
        assertTrue(book.callOf(alice) != null);
        book.sessionBack(alice);
        book.tick(20_000);
        assertTrue(book.callOf(alice) != null);
        book.sessionLost(alice, 30_000);
        book.tick(40_000);
        assertNull(book.callOf(alice));
        assertEquals("ended SESSION_LOST after connecting", events.get(events.size() - 1));
    }

    @Test
    void logoutAndAVanishedPhoneEndCalls() {
        connect();
        book.logout(bob);
        assertNull(book.callOf(alice));
        assertEquals("ended LOGOUT after connecting", events.get(events.size() - 1));
        connect();
        book.phoneGone(PHONE_A);
        assertNull(book.callOf(bob));
        assertEquals("ended PHONE_GONE after connecting", events.get(events.size() - 1));
    }

    @Test
    void everyChangeMovesTheVersion() {
        long v0 = book.version();
        connect();
        long v1 = book.version();
        assertTrue(v1 > v0);
        book.evaluate(2000, quality);
        assertTrue(book.version() > v1);
        long v2 = book.version();
        book.hangUp(alice);
        assertTrue(book.version() > v2);
    }

    @Test
    void oneCallCanBeEvaluatedTheMomentItConnects() {
        Call call = connect();
        assertEquals(SignalLevel.NONE, call.level(alice, bob));
        book.evaluate(call, 1000, quality);
        assertEquals(SignalLevel.LOW, call.level(alice, bob));
        assertEquals(SignalLevel.LOW, call.level(bob, alice));
    }

    @Test
    void aRingingCallEndsOnceItsPathHasBeenGoneForTheGrace() {
        assertEquals(CallBook.DialResult.RINGING, book.dial(alice, PHONE_A, PHONE_B, 0, CallBook.Check.OK));
        // Below Low, a ringing call gets the same 3 s of grace a connected one does.
        snr = 3;
        book.evaluate(1000, quality);
        book.evaluate(4000, quality);
        assertTrue(
            book.callOfPhone(PHONE_B) != null && book.callOfPhone(PHONE_B)
                .state() == Call.State.RINGING);
        book.evaluate(4001, quality);
        assertNull(book.callOfPhone(PHONE_B));
        assertTrue(events.contains("ended DROPPED unanswered"));
    }

    @Test
    void aPathBackInTimeKeepsTheRing() {
        assertEquals(CallBook.DialResult.RINGING, book.dial(alice, PHONE_A, PHONE_B, 0, CallBook.Check.OK));
        snr = 3;
        book.evaluate(1000, quality);
        snr = 18;
        book.evaluate(2000, quality);
        snr = 3;
        book.evaluate(4500, quality);
        assertTrue(book.callOfPhone(PHONE_B) != null);
    }

    @Test
    void aCallAnsweredFromARingStartsAtLow() {
        // The ring held a path at Low or better, so the hysteresis carries the answered call in the band just below.
        assertEquals(CallBook.DialResult.RINGING, book.dial(alice, PHONE_A, PHONE_B, 0, CallBook.Check.OK));
        snr = 5;
        book.evaluate(1000, quality);
        assertTrue(book.answer(bob, PHONE_B, 1500));
        Call call = book.callOf(alice);
        book.evaluate(call, 1500, quality);
        assertEquals(SignalLevel.LOW, call.level(alice, bob));
        assertEquals(SignalLevel.LOW, call.level(bob, alice));
    }

    @Test
    void answeringDoesntRestartTheDropGrace() {
        // Below Low since 1 s into the ring, answered at 3.9 s: the 3 s of grace run out at 4 s, not 6.9 s.
        snr = 7;
        assertEquals(CallBook.DialResult.RINGING, book.dial(alice, PHONE_A, PHONE_B, 0, CallBook.Check.OK));
        snr = 3;
        book.evaluate(1000, quality);
        assertTrue(book.answer(bob, PHONE_B, 3900));
        Call call = book.callOf(alice);
        book.evaluate(call, 4100, quality);
        assertTrue(events.contains("ended DROPPED after connecting"), events.toString());
    }

    private final UUID dave = UUID.randomUUID();
    private static final int PHONE_D = 444_444;
    private static final int PHONE_E = 555_555;

    /** Alice, Bob and Carol, on phones A, B and C, Carol added by Bob. */
    private Call groupOfThree() {
        Call call = connect();
        assertEquals(CallBook.DialResult.RINGING, book.add(bob, PHONE_C, 1500, CallBook.Check.OK));
        assertTrue(book.answer(carol, PHONE_C, 2000));
        return call;
    }

    @Test
    void anyoneInACallCanAddAPhoneThatRingsAndJoins() {
        Call call = groupOfThree();
        assertEquals(List.of(alice, bob, carol), call.participants());
        assertEquals(call, book.callOf(carol));
        assertEquals(
            PHONE_B,
            call.legOf(PHONE_C)
                .invitedBy());
        book.evaluate(3000, quality);
        assertEquals(SignalLevel.LOW, call.level(alice, carol));
        assertEquals(SignalLevel.LOW, call.level(carol, bob));
    }

    @Test
    void onlyAConnectedCallTakesMorePhones() {
        assertEquals(CallBook.DialResult.RINGING, book.dial(alice, PHONE_A, PHONE_B, 0, CallBook.Check.OK));
        assertEquals(CallBook.DialResult.INVALID, book.add(alice, PHONE_C, 500, CallBook.Check.OK));
        assertTrue(book.answer(bob, PHONE_B, 1000));
        // Not someone outside it, nor a phone already in it.
        assertEquals(CallBook.DialResult.INVALID, book.add(carol, PHONE_C, 1500, CallBook.Check.OK));
        assertEquals(CallBook.DialResult.INVALID, book.add(alice, PHONE_B, 1500, CallBook.Check.OK));
    }

    @Test
    void aGroupCallTakesUpToItsSize() {
        groupOfThree();
        assertEquals(CallBook.DialResult.RINGING, book.add(alice, PHONE_D, 2500, CallBook.Check.OK));
        // Three people and a ringing phone make four, the size here.
        assertEquals(CallBook.DialResult.FULL, book.add(alice, PHONE_E, 2600, CallBook.Check.OK));
    }

    @Test
    void addingRingsBusyAndUnreachableLikeDialling() {
        connect();
        assertEquals(CallBook.DialResult.UNREACHABLE, book.add(alice, PHONE_C, 1500, CallBook.Check.UNREACHABLE));
        assertEquals(CallBook.DialResult.RINGING, book.dial(carol, PHONE_C, PHONE_D, 1500, CallBook.Check.OK));
        assertEquals(CallBook.DialResult.BUSY, book.add(alice, PHONE_D, 1600, CallBook.Check.OK));
    }

    @Test
    void anAddedPhoneThatIsntAnsweredLeavesAndTheCallGoesOn() {
        Call call = connect();
        book.add(alice, PHONE_C, 2000, CallBook.Check.OK);
        book.tick(31_999);
        assertEquals(call, book.callOfPhone(PHONE_C));
        book.tick(32_000);
        assertNull(book.callOfPhone(PHONE_C));
        assertEquals(Call.State.CONNECTED, call.state());
        assertTrue(events.contains("left " + PHONE_C + " NO_ANSWER"), events.toString());
        // Rejected the same way.
        book.add(alice, PHONE_D, 33_000, CallBook.Check.OK);
        assertTrue(book.reject(PHONE_D));
        assertEquals(Call.State.CONNECTED, call.state());
        assertTrue(events.contains("left " + PHONE_D + " REJECTED"), events.toString());
    }

    @Test
    void hangingUpInAGroupLeavesItUntilTwoAreLeft() {
        Call call = groupOfThree();
        assertTrue(book.hangUp(alice));
        assertEquals(Call.State.CONNECTED, call.state());
        assertNull(book.callOf(alice));
        assertNull(book.callOfPhone(PHONE_A));
        assertEquals(List.of(bob, carol), call.participants());
        assertTrue(book.hangUp(bob));
        assertEquals(Call.State.ENDED, call.state());
        assertNull(book.callOf(carol));
    }

    @Test
    void aPhoneCutOffFromEveryoneDropsOutOfAGroup() {
        // Carol's phone loses its path to both others; Alice and Bob go on.
        Call call = groupOfThree();
        book.evaluate(3000, quality);
        CallBook.Quality carolGone = new CallBook.Quality() {

            @Override
            public double snr(Call c, Call.Leg speaker, Call.Leg listener) {
                return speaker.phone() == PHONE_C || listener.phone() == PHONE_C ? 2 : 18;
            }

            @Override
            public SignalLevel cap(Call c, Call.Leg speaker, Call.Leg listener) {
                return SignalLevel.LOW;
            }
        };
        book.evaluate(4000, carolGone);
        book.evaluate(7000, carolGone);
        assertEquals(call, book.callOf(carol));
        book.evaluate(7001, carolGone);
        assertNull(book.callOf(carol));
        assertEquals(Call.State.CONNECTED, call.state());
        assertTrue(events.contains("left " + PHONE_C + " DROPPED"), events.toString());
    }

    @Test
    void twoPhonesCutOffFromEachOtherStayWhileTheyReachAThird() {
        Call call = groupOfThree();
        CallBook.Quality abGone = new CallBook.Quality() {

            @Override
            public double snr(Call c, Call.Leg speaker, Call.Leg listener) {
                boolean ab = speaker.phone() != PHONE_C && listener.phone() != PHONE_C;
                return ab ? 2 : 18;
            }

            @Override
            public SignalLevel cap(Call c, Call.Leg speaker, Call.Leg listener) {
                return SignalLevel.LOW;
            }
        };
        book.evaluate(3000, abGone);
        book.evaluate(7000, abGone);
        assertEquals(List.of(alice, bob, carol), call.participants());
        assertEquals(SignalLevel.NONE, call.level(alice, bob));
        assertEquals(SignalLevel.LOW, call.level(alice, carol));
    }

    @Test
    void aSpeakerphonesRoomJoinsAndLeavesItsCall() {
        Call call = connect();
        book.setRoom(PHONE_B, List.of(bob, carol), 1500);
        assertEquals(List.of(alice, bob, carol), call.participants());
        assertEquals(call, book.callOf(carol));
        assertTrue(
            call.legOf(PHONE_B)
                .speakerphone());
        book.evaluate(2000, quality);
        assertEquals(SignalLevel.LOW, call.level(alice, carol));
        assertEquals(SignalLevel.LOW, levels.get(alice + ">" + carol));
        // Bob walks away: he's out, though he answered; the phone stays in the call.
        book.setRoom(PHONE_B, List.of(carol), 2500);
        assertNull(book.callOf(bob));
        assertEquals(List.of(alice, carol), call.participants());
        assertTrue(events.contains("room " + PHONE_B + " +0 -1"), events.toString());
    }

    @Test
    void aRoomTakesNobodyInAnotherCallNorPastTheGroupSize() {
        Call call = connect();
        assertEquals(CallBook.DialResult.RINGING, book.dial(dave, PHONE_D, PHONE_E, 1500, CallBook.Check.OK));
        UUID erin = UUID.randomUUID();
        UUID frank = UUID.randomUUID();
        book.setRoom(PHONE_B, List.of(bob, dave, carol, erin, frank), 2000);
        // Dave is ringing someone; Alice, Bob, Carol and Erin make four.
        assertEquals(List.of(alice, bob, carol, erin), call.participants());
        assertNull(book.callOf(frank));
    }

    @Test
    void aRoomPlayerLoggingOutLeavesTheRoomNotTheCall() {
        Call call = connect();
        book.setRoom(PHONE_B, List.of(bob, carol), 1500);
        book.logout(carol);
        assertEquals(Call.State.CONNECTED, call.state());
        assertEquals(List.of(alice, bob), call.participants());
        // Hanging up from the room hangs up the speakerphone.
        assertTrue(book.hangUp(bob));
        assertEquals(Call.State.ENDED, call.state());
    }

    @Test
    void anEmptyRoomHangsUpItsSpeakerphoneAfterAWhile() {
        Call call = connect();
        book.setRoom(PHONE_B, List.of(bob), 1500);
        book.setRoom(PHONE_B, List.of(), 3000);
        assertEquals(List.of(alice), call.participants());
        book.tick(3000 + 29_999);
        assertEquals(Call.State.CONNECTED, call.state());
        book.tick(3000 + 30_000);
        assertEquals(Call.State.ENDED, call.state());
        assertTrue(events.contains("ended HUNG_UP after connecting"), events.toString());
    }

    @Test
    void someoneComingBackRestartsTheEmptyRoomClock() {
        Call call = connect();
        book.setRoom(PHONE_B, List.of(), 3000);
        book.setRoom(PHONE_B, List.of(carol), 20_000);
        // Still empty a second later: the clock started when the room emptied, not at each look.
        book.setRoom(PHONE_B, List.of(), 25_000);
        book.setRoom(PHONE_B, List.of(), 26_000);
        book.tick(54_999);
        assertEquals(Call.State.CONNECTED, call.state());
        book.tick(55_000);
        assertEquals(Call.State.ENDED, call.state());
    }

    @Test
    void anEmptySpeakerphoneLeavesAGroupCallThatGoesOn() {
        Call call = connect();
        assertEquals(CallBook.DialResult.RINGING, book.add(alice, PHONE_C, 1500, CallBook.Check.OK));
        assertTrue(book.answer(carol, PHONE_C, 2000));
        book.setRoom(PHONE_B, List.of(), 3000);
        book.tick(33_000);
        assertEquals(Call.State.CONNECTED, call.state());
        assertNull(call.legOf(PHONE_B));
        assertEquals(List.of(alice, carol), call.participants());
        assertTrue(events.contains("left " + PHONE_B + " HUNG_UP"), events.toString());
    }
}
