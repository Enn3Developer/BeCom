package com.enn3developer.becom.gui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.PacketBuffer;

import org.junit.jupiter.api.Test;

import io.netty.buffer.Unpooled;

/** The Router's screen gets what the server built, and a router with crowded buses sends a bounded view. */
class RouterViewTest {

    private static RouterView roundTrip(RouterView view) {
        PacketBuffer buf = new PacketBuffer(Unpooled.buffer());
        view.write(buf);
        return RouterView.read(buf);
    }

    @Test
    void roundTripsEveryField() {
        RouterView view = new RouterView(
            1,
            true,
            "Steve",
            List.of(new RouterView.Uplink(2, 100, 70, -200, 45, new int[] { 2, 0, 1 }, 32, 12, 5)),
            3,
            8,
            List.of(
                new RouterView.Bus(0, 40, 128, 16, 4, 2, 3, 1, 0),
                new RouterView.Bus(2, 4097, 4096, 0, 0, 0, 1, 0, 1)),
            List.of(
                new RouterView.Device(0, -1, 482913, 0, RouterView.Now.CALL, "Alex"),
                new RouterView.Device(4, -1, 300210, 1, RouterView.Now.TOO_LONG, ""),
                new RouterView.Device(-1, 2, -1, 0, RouterView.Now.JOINED, "")));
        RouterView back = roundTrip(view);
        assertEquals(view.tier(), back.tier());
        assertEquals(view.working(), back.working());
        assertEquals(view.owner(), back.owner());
        assertEquals(view.calls(), back.calls());
        assertEquals(view.slots(), back.slots());
        assertEquals(view.buses(), back.buses());
        assertEquals(view.devices(), back.devices());
        RouterView.Uplink u = view.uplinks()
            .get(0),
            b = back.uplinks()
                .get(0);
        assertEquals(
            List.of(u.kind(), u.x(), u.y(), u.z(), u.distance(), u.rate(), u.used(), u.data()),
            List.of(b.kind(), b.x(), b.y(), b.z(), b.distance(), b.rate(), b.used(), b.data()));
        assertArrayEquals(u.cables(), b.cables());
    }

    @Test
    void capsTheDevicesItSends() {
        List<RouterView.Device> many = new ArrayList<>();
        for (int i = 0; i < RouterView.MAX_DEVICES + 10; i++) {
            many.add(new RouterView.Device(0, -1, i, 0, RouterView.Now.IDLE, ""));
        }
        List<RouterView.Bus> buses = new ArrayList<>();
        for (int i = 0; i < RouterView.MAX_BUSES + 2; i++) buses.add(new RouterView.Bus(0, i, 128, 16, 0, 0, 1, 0, 0));
        RouterView back = roundTrip(new RouterView(0, false, "", List.of(), 0, 8, buses, many));
        assertEquals(
            RouterView.MAX_DEVICES,
            back.devices()
                .size());
        assertEquals(
            RouterView.MAX_BUSES,
            back.buses()
                .size());
    }

    @Test
    void anEmptyViewRoundTrips() {
        assertEquals(RouterView.EMPTY, roundTrip(RouterView.EMPTY));
    }
}
