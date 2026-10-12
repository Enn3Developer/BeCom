package com.enn3developer.becom.logic.call;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.enn3developer.becom.logic.SignalLevel;
import com.enn3developer.becom.logic.Tunables;
import com.enn3developer.becom.logic.phone.PhoneKind;

/**
 * DESIGN.md 15.5: who First call, Mobile call, ME Bridge call and Call between dimensions complete for as a call
 * connects.
 */
class CallQuestsTest {

    private static final int MOBILE = 111_111;
    private static final int TELEPHONE = 222_222;
    private static final int ME_A = 333_333;
    private static final int ME_B = 444_444;
    private static final int VIDEOPHONE = 555_555;

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final UUID carol = UUID.randomUUID();
    private final Map<Integer, PhoneKind> kinds = Map.of(
        MOBILE,
        PhoneKind.MOBILE,
        TELEPHONE,
        PhoneKind.TELEPHONE,
        ME_A,
        PhoneKind.ME_PHONE,
        ME_B,
        PhoneKind.ME_PHONE,
        VIDEOPHONE,
        PhoneKind.VIDEOPHONE);

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

    private Call call(UUID caller, int from, UUID callee, int to) {
        assertEquals(CallBook.DialResult.RINGING, book.dial(caller, from, to, 0, CallBook.Check.OK));
        book.answer(callee, to, 100);
        return book.callOf(caller);
    }

    @Test
    void aMobilePhoneCallCompletesMobileCallForItsUserOnly() {
        Call call = call(alice, MOBILE, bob, TELEPHONE);
        CallQuests.Done done = CallQuests.of(call, kinds::get, (from, to) -> false, p -> 0);
        assertEquals(List.of(alice, bob), done.firstCall());
        assertEquals(List.of(alice), done.mobileCall());
        assertEquals(List.of(), done.meBridgeCall());
    }

    @Test
    void anMePhoneCallThroughABridgeCompletesForTheMePhonesUser() {
        Call call = call(alice, TELEPHONE, bob, ME_A);
        CallQuests.Done done = CallQuests.of(call, kinds::get, (from, to) -> from == ME_A && to == TELEPHONE, p -> 0);
        assertEquals(List.of(bob), done.meBridgeCall());
        assertEquals(List.of(), done.mobileCall());
    }

    @Test
    void twoMePhonesOnOneNetworkUseNoBridge() {
        Call call = call(alice, ME_A, bob, ME_B);
        assertEquals(
            List.of(),
            CallQuests.of(call, kinds::get, (from, to) -> false, p -> 0)
                .meBridgeCall());
    }

    @Test
    void aSpeakerphonesRoomCountsForFirstCall() {
        Call call = call(alice, MOBILE, bob, VIDEOPHONE);
        book.setRoom(VIDEOPHONE, List.of(bob, carol), 200);
        CallQuests.Done done = CallQuests.of(call, kinds::get, (from, to) -> false, p -> 0);
        assertEquals(List.of(alice, bob, carol), done.firstCall());
        assertEquals(List.of(alice), done.mobileCall());
    }

    @Test
    void aCallBetweenDimensionsCompletesForEveryoneInIt() {
        Call call = call(alice, MOBILE, bob, TELEPHONE);
        Map<UUID, Integer> where = Map.of(alice, 0, bob, -28);
        assertEquals(
            List.of(alice, bob),
            CallQuests.of(call, kinds::get, (from, to) -> false, where::get)
                .betweenDimensions());
        // Both in the Overworld: no.
        assertEquals(
            List.of(),
            CallQuests.of(call, kinds::get, (from, to) -> false, p -> 0)
                .betweenDimensions());
    }

    @Test
    void someoneOfflineDoesntMakeACallSpanDimensions() {
        Call call = call(alice, MOBILE, bob, TELEPHONE);
        Map<UUID, Integer> where = Map.of(alice, -1);
        assertEquals(
            List.of(),
            CallQuests.of(call, kinds::get, (from, to) -> false, where::get)
                .betweenDimensions());
    }

    @Test
    void aSpeakerphonesRoomInAnotherDimensionCounts() {
        // Carol joins the Videophone's room; she and Bob are in the Nether, Alice in the Overworld.
        Call call = call(alice, MOBILE, bob, VIDEOPHONE);
        book.setRoom(VIDEOPHONE, List.of(bob, carol), 200);
        Map<UUID, Integer> where = Map.of(alice, 0, bob, -1, carol, -1);
        assertEquals(
            List.of(alice, bob, carol),
            CallQuests.of(call, kinds::get, (from, to) -> false, where::get)
                .betweenDimensions());
    }

    @Test
    void aRingingCallCompletesNothing() {
        book.dial(alice, MOBILE, TELEPHONE, 0, CallBook.Check.OK);
        assertEquals(CallQuests.Done.NONE, CallQuests.of(book.callOf(alice), kinds::get, (from, to) -> true, p -> 1));
    }
}
