package com.enn3developer.becom.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import net.minecraft.network.PacketBuffer;

import org.junit.jupiter.api.Test;

import com.enn3developer.becom.logic.SignalLevel;
import com.enn3developer.becom.logic.app.AppManifest;
import com.enn3developer.becom.logic.app.AppTree;
import com.enn3developer.becom.logic.phone.PhoneRecord;

import io.netty.buffer.Unpooled;

/** The phone screen's view survives the wire whole, up to each list's cap. */
class PhoneViewTest {

    private static PhoneView view(int contacts, int blocked) {
        List<PhoneView.Contact> c = new ArrayList<>();
        for (int i = 0; i < contacts; i++) c.add(new PhoneView.Contact(100_000 + i, "owner" + i, i % 3));
        List<PhoneView.Blocked> b = new ArrayList<>();
        for (int i = 0; i < blocked; i++) b.add(new PhoneView.Blocked(UUID.randomUUID(), "pest" + i));
        List<PhoneView.Recent> r = List
            .of(new PhoneView.Recent(1_700_000_000_000L, 123_456, "bob", PhoneRecord.Direction.MISSED));
        return new PhoneView(
            654_321,
            "alice",
            true,
            PhoneView.State.CONNECTED,
            "bob",
            123_456,
            SignalLevel.LOW,
            false,
            true,
            c,
            r,
            b,
            true,
            true,
            false,
            SignalLevel.HIGH,
            List.of(
                new PhoneView.Member(123_456, "bob", SignalLevel.LOW, false),
                new PhoneView.Member(222_222, "carol", SignalLevel.NONE, true)),
            false,
            new PhoneView.Video(true, false, true, true, true),
            new PhoneView.Battery(
                744_000,
                1_200_000,
                155,
                39,
                true,
                List.of(42, 24, 21, 9, 4),
                false,
                true,
                true,
                List.of(new PhoneView.AppShare("Reactor", 4))),
            new PhoneView.Via(PhoneView.Via.Kind.ANTENNA, 2),
            new AppsView(
                List.of(
                    new AppsView.App(
                        "reactor",
                        "Reactor",
                        "1.2",
                        "Steve",
                        "Hot",
                        0x2a8a4a,
                        "0".repeat(32) + "a".repeat(32),
                        0b10101,
                        false,
                        "120, 64, -30",
                        true,
                        14,
                        3000,
                        3072,
                        12_000,
                        65_536,
                        AppManifest.Use.CALL.bit())),
                new AppsView.Screen(
                    "reactor",
                    AppsView.State.RUNNING,
                    "",
                    List.of(
                        new AppTree.Widget(
                            AppTree.Kind.PROGRESS,
                            "",
                            "Heat",
                            "",
                            0.5,
                            AppTree.Level.HIGH,
                            AppTree.Style.PLAIN,
                            ""),
                        new AppTree.Widget(
                            AppTree.Kind.BUTTON,
                            "scram",
                            "SCRAM",
                            "",
                            0,
                            AppTree.Level.PLAIN,
                            AppTree.Style.DANGER,
                            "")),
                    123_456_789L),
                new AppsView.Offer(7, true, "Lights", "1.0", "Alex", "1, 2, 3", 900, 3, 0x2f5f9f, "e".repeat(64)),
                new AppsView.Notice("Reactor", "Hull heat", "80%"),
                List.of(
                    new AppsView.StoreApp(
                        "lights",
                        "Lights",
                        "1.0",
                        "Alex",
                        "Base lights",
                        4,
                        900,
                        "",
                        "f".repeat(64),
                        1,
                        12,
                        true,
                        true,
                        0x6a5a1a)),
                "li",
                2,
                3,
                17),
            new PhoneView.Phone(2, "Base", true, 1_700_000_000_000L, true, List.of(300417, 482917, 0, 0), 2, true));
    }

    private static PhoneView roundTrip(PhoneView view) {
        PacketBuffer buf = new PacketBuffer(Unpooled.buffer());
        view.write(buf);
        return PhoneView.read(buf);
    }

    @Test
    void roundTripsEveryField() {
        PhoneView view = view(3, 2);
        assertEquals(view, roundTrip(view));
    }

    @Test
    void keepsMoreThanSixtyFourContacts() {
        PhoneView back = roundTrip(view(70, 0));
        assertEquals(
            70,
            back.contacts()
                .size());
    }

    @Test
    void capsContactsAndBlockedPlayers() {
        PhoneView back = roundTrip(view(PhoneView.MAX_CONTACTS + 10, PhoneView.MAX_BLOCKED + 10));
        assertEquals(
            PhoneView.MAX_CONTACTS,
            back.contacts()
                .size());
        assertEquals(
            PhoneView.MAX_BLOCKED,
            back.blocked()
                .size());
    }

    @Test
    void anEmptyViewRoundTrips() {
        assertEquals(PhoneView.EMPTY, roundTrip(PhoneView.EMPTY));
        assertEquals(Collections.emptyList(), roundTrip(PhoneView.EMPTY).contacts());
    }
}
