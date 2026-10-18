package com.enn3developer.becom.logic.video;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.enn3developer.becom.logic.SignalLevel;
import com.enn3developer.becom.logic.Tunables;
import com.enn3developer.becom.logic.call.Call;
import com.enn3developer.becom.logic.call.CallBook;
import com.enn3developer.becom.logic.phone.PhoneKind;

/** Who sees whom in a video call, at which level, and where the rear camera goes. */
class VideoPlanTest {

    private static final int MOBILE_A = 111_111;
    private static final int MOBILE_B = 222_222;
    private static final int TELEPHONE = 333_333;
    private static final int VIDEOPHONE = 444_444;
    private static final int ME_PHONE = 555_555;

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final UUID carol = UUID.randomUUID();
    private final UUID dan = UUID.randomUUID();
    private final Map<Integer, PhoneKind> kinds = new HashMap<>();
    private final Map<UUID, VideoPlan.Cameras> cameras = new HashMap<>();
    private double snr = 30;

    {
        kinds.put(MOBILE_A, PhoneKind.MOBILE);
        kinds.put(MOBILE_B, PhoneKind.MOBILE);
        kinds.put(TELEPHONE, PhoneKind.TELEPHONE);
        kinds.put(VIDEOPHONE, PhoneKind.VIDEOPHONE);
        kinds.put(ME_PHONE, PhoneKind.ME_PHONE);
    }

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
            return snr;
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

    private Call call(UUID caller, int from, UUID callee, int to) {
        assertEquals(CallBook.DialResult.RINGING, book.dial(caller, from, to, 0, CallBook.Check.OK));
        assertTrue(book.answer(callee, to, 100));
        return book.callOf(caller);
    }

    private VideoPlan plan(Call call) {
        call.setVideo(true);
        book.evaluate(call, 1000, quality);
        return VideoPlan.of(
            call,
            kinds::get,
            player -> cameras.getOrDefault(player, VideoPlan.Cameras.START),
            (seen, viewing) -> true);
    }

    @Test
    void aVoiceCallShowsNoVideo() {
        Call call = call(alice, MOBILE_A, bob, MOBILE_B);
        book.evaluate(call, 1000, quality);
        assertTrue(
            VideoPlan.of(call, kinds::get, player -> VideoPlan.Cameras.START, (seen, viewing) -> true)
                .tilesOf(alice)
                .isEmpty());
    }

    @Test
    void aStreamThatFellBackToVoiceFreezesThePicture() {
        Call call = call(alice, MOBILE_A, bob, MOBILE_B);
        call.setVideo(true);
        book.evaluate(call, 1000, quality);
        VideoPlan plan = VideoPlan.of(call, kinds::get, player -> VideoPlan.Cameras.START, (seen, viewing) -> false);
        assertFalse(plan.seesLive(alice));
    }

    @Test
    void aMobilePhoneAndAVideophoneSeeEachOtherAtTheirCommonCap() {
        Call call = call(alice, MOBILE_A, bob, VIDEOPHONE);
        book.setRoom(VIDEOPHONE, List.of(bob, carol), 200);
        VideoPlan plan = plan(call);
        assertEquals(
            List.of(
                new VideoPlan.Tile(bob, VIDEOPHONE, SignalLevel.HIGH, true, false),
                new VideoPlan.Tile(carol, VIDEOPHONE, SignalLevel.HIGH, true, false)),
            plan.tilesOf(alice),
            "the other side sees everyone in the Videophone's room as front-camera tiles");
        assertEquals(List.of(new VideoPlan.Tile(alice, MOBILE_A, SignalLevel.HIGH, true, false)), plan.tilesOf(bob));
        assertEquals(
            plan.tilesOf(bob),
            plan.tilesOf(carol),
            "the Videophone's screen shows the call to its whole room");
    }

    @Test
    void telephonesAreVoiceOnly() {
        Call call = call(alice, MOBILE_A, dan, TELEPHONE);
        VideoPlan plan = plan(call);
        assertTrue(
            plan.tilesOf(alice)
                .isEmpty());
        assertTrue(
            plan.tilesOf(dan)
                .isEmpty());
    }

    @Test
    void aGroupCallShowsEveryoneOnAVideoPhone() {
        Call call = call(alice, MOBILE_A, bob, MOBILE_B);
        assertEquals(CallBook.DialResult.RINGING, book.add(alice, TELEPHONE, 300, CallBook.Check.OK));
        assertTrue(book.answer(dan, TELEPHONE, 400));
        assertEquals(CallBook.DialResult.RINGING, book.add(alice, VIDEOPHONE, 500, CallBook.Check.OK));
        assertTrue(book.answer(carol, VIDEOPHONE, 600));
        VideoPlan plan = plan(call);
        assertEquals(
            List.of(bob, carol),
            plan.tilesOf(alice)
                .stream()
                .map(VideoPlan.Tile::source)
                .toList(),
            "Dan is on a Telephone: heard, not seen");
        assertTrue(
            plan.tilesOf(dan)
                .isEmpty());
    }

    @Test
    void videoFreezesAtLowAndIsLiveFromMedium() {
        snr = 8;
        Call call = call(alice, MOBILE_A, bob, MOBILE_B);
        VideoPlan.Tile low = plan(call).tilesOf(bob)
            .get(0);
        assertEquals(SignalLevel.LOW, low.level());
        assertFalse(low.live(), "At Low the picture freezes");
        snr = 14;
        VideoPlan.Tile medium = plan(call).tilesOf(bob)
            .get(0);
        assertEquals(SignalLevel.MEDIUM, medium.level());
        assertTrue(medium.live());
        assertEquals(Map.of(bob, medium), plan(call).liveViewersOf(alice));
    }

    @Test
    void aFrontCameraSwitchedOffStillShowsItsTileButIsntLive() {
        cameras.put(alice, new VideoPlan.Cameras(false, false));
        Call call = call(alice, MOBILE_A, bob, MOBILE_B);
        VideoPlan plan = plan(call);
        VideoPlan.Tile tile = plan.tilesOf(bob)
            .get(0);
        assertFalse(tile.front());
        assertFalse(tile.live());
        assertTrue(
            plan.liveViewersOf(alice)
                .isEmpty());
    }

    @Test
    void theRearCameraGoesOneToOneFromACarriedPhoneAtHighOrBetter() {
        cameras.put(alice, new VideoPlan.Cameras(true, true));
        Call call = call(alice, MOBILE_A, bob, ME_PHONE);
        VideoPlan plan = plan(call);
        assertTrue(
            plan.tilesOf(bob)
                .get(0)
                .rear());
        assertEquals(SignalLevel.HIGH, plan.rearSendOf(alice), "the Mobile Phone caps the call at High");
        assertNull(plan.rearSendOf(bob), "Bob's rear camera starts off");
        snr = 14;
        assertNull(plan(call).rearSendOf(alice), "not at Medium");
    }

    @Test
    void theRearCameraStopsInAGroupCall() {
        cameras.put(alice, new VideoPlan.Cameras(true, true));
        Call call = call(alice, MOBILE_A, bob, MOBILE_B);
        assertEquals(SignalLevel.HIGH, plan(call).rearSendOf(alice));
        assertEquals(CallBook.DialResult.RINGING, book.add(alice, ME_PHONE, 300, CallBook.Check.OK));
        assertTrue(book.answer(carol, ME_PHONE, 400));
        VideoPlan plan = plan(call);
        assertNull(plan.rearSendOf(alice));
        assertFalse(
            plan.tilesOf(bob)
                .get(0)
                .rear());
    }

    @Test
    void aVideophoneHasNoRearCameraAndCountsItsRoom() {
        cameras.put(bob, new VideoPlan.Cameras(true, true));
        cameras.put(alice, new VideoPlan.Cameras(true, true));
        Call call = call(alice, MOBILE_A, bob, VIDEOPHONE);
        book.setRoom(VIDEOPHONE, List.of(bob), 200);
        VideoPlan plan = plan(call);
        assertNull(plan.rearSendOf(bob), "a Videophone shows its room, not a first-person view");
        assertEquals(SignalLevel.HIGH, plan.rearSendOf(alice), "two people in all is one-to-one");
        book.setRoom(VIDEOPHONE, List.of(bob, carol), 300);
        assertNull(plan(call).rearSendOf(alice), "with two in the room it's three people");
    }

    /** Video call completes for a viewer of live video only. */
    @Test
    void theVideoCallTaskGoesToViewersOfLiveVideo() {
        snr = 8;
        cameras.put(alice, new VideoPlan.Cameras(false, false));
        Call call = call(alice, MOBILE_A, bob, MOBILE_B);
        assertFalse(plan(call).seesLive(bob), "frozen at Low");
        snr = 14;
        VideoPlan plan = plan(call);
        assertFalse(plan.seesLive(bob), "Alice's front camera is off");
        assertTrue(plan.seesLive(alice), "Bob's is on, at Medium");
        Call voice = call(carol, TELEPHONE, dan, ME_PHONE);
        assertFalse(plan(voice).seesLive(dan), "a Telephone has no camera");
    }

    @Test
    void aCallStillRingingShowsNothing() {
        assertEquals(CallBook.DialResult.RINGING, book.dial(alice, MOBILE_A, MOBILE_B, 0, CallBook.Check.OK));
        Call ringing = book.callOf(alice);
        ringing.setVideo(true);
        VideoPlan plan = VideoPlan.of(ringing, kinds::get, player -> VideoPlan.Cameras.START, (seen, viewing) -> true);
        assertTrue(
            plan.tilesOf(alice)
                .isEmpty());
    }
}
