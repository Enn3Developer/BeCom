package com.enn3developer.becom.logic.call;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.enn3developer.becom.logic.SignalLevel;
import com.enn3developer.becom.logic.Tunables;

/** DESIGN.md 10: call states, one call at a time, ringing, levels per direction, drops and session grace. */
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
        new CallBook.Listener() {

            @Override
            public void ringing(Call call) {
                events.add("ringing");
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
            public void levelChanged(Call call, UUID speaker, UUID listener, SignalLevel level) {
                levels.put(speaker + ">" + listener, level);
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
        assertEquals(List.of("ringing", "connected"), events);
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
        // 18 dB is High, but a Telephone caps it at Low (D10).
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
    void aRingingCallEndsWhenItsPathGoes() {
        assertEquals(CallBook.DialResult.RINGING, book.dial(alice, PHONE_A, PHONE_B, 0, CallBook.Check.OK));
        // Just under Low still rings, thanks to the hysteresis; past it the line is gone.
        snr = 5;
        book.evaluate(1000, quality);
        assertTrue(
            book.callOfPhone(PHONE_B) != null && book.callOfPhone(PHONE_B)
                .state() == Call.State.RINGING);
        snr = 3;
        book.evaluate(2000, quality);
        assertNull(book.callOfPhone(PHONE_B));
        assertTrue(events.contains("ended DROPPED unanswered"));
    }
}
