package com.enn3developer.becom.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import net.minecraft.network.PacketBuffer;

import org.junit.jupiter.api.Test;

import com.enn3developer.becom.logic.SignalLevel;
import com.enn3developer.becom.logic.phone.PhoneRecord;

import io.netty.buffer.Unpooled;

/** The phone screen's view survives the wire whole, up to each list's cap (DESIGN.md 11.2). */
class PhoneViewTest {

    private static PhoneView view(int contacts, int blocked) {
        List<PhoneView.Contact> c = new ArrayList<>();
        for (int i = 0; i < contacts; i++) c.add(new PhoneView.Contact(100_000 + i, "owner" + i));
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
            b);
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
