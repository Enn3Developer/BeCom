package com.enn3developer.becom.gui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.PacketBuffer;

import org.junit.jupiter.api.Test;

import io.netty.buffer.Unpooled;

/** The Line Terminal's screen gets what the server built, and a terminal with crowded buses sends a bounded view. */
class LineTerminalViewTest {

    private static LineTerminalView roundTrip(LineTerminalView view) {
        PacketBuffer buf = new PacketBuffer(Unpooled.buffer());
        view.write(buf);
        return LineTerminalView.read(buf);
    }

    @Test
    void roundTripsEveryField() {
        LineTerminalView view = new LineTerminalView(
            1,
            true,
            "Steve",
            List.of(new LineTerminalView.Uplink(2, 100, 70, -200, 45, new int[] { 2, 0, 1 }, 32, 12, 5)),
            3,
            8,
            List.of(
                new LineTerminalView.Bus(0, 40, 128, 16, 4, 2, 3, 1, 0),
                new LineTerminalView.Bus(2, 4097, 4096, 0, 0, 0, 1, 0, 1)),
            List.of(
                new LineTerminalView.Device(0, -1, 482913, 0, LineTerminalView.Now.CALL, "Alex"),
                new LineTerminalView.Device(4, -1, 300210, 1, LineTerminalView.Now.TOO_LONG, ""),
                new LineTerminalView.Device(-1, 2, -1, 0, LineTerminalView.Now.JOINED, "")));
        LineTerminalView back = roundTrip(view);
        assertEquals(view.tier(), back.tier());
        assertEquals(view.working(), back.working());
        assertEquals(view.owner(), back.owner());
        assertEquals(view.calls(), back.calls());
        assertEquals(view.slots(), back.slots());
        assertEquals(view.buses(), back.buses());
        assertEquals(view.devices(), back.devices());
        LineTerminalView.Uplink u = view.uplinks()
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
        List<LineTerminalView.Device> many = new ArrayList<>();
        for (int i = 0; i < LineTerminalView.MAX_DEVICES + 10; i++) {
            many.add(new LineTerminalView.Device(0, -1, i, 0, LineTerminalView.Now.IDLE, ""));
        }
        List<LineTerminalView.Bus> buses = new ArrayList<>();
        for (int i = 0; i < LineTerminalView.MAX_BUSES + 2; i++)
            buses.add(new LineTerminalView.Bus(0, i, 128, 16, 0, 0, 1, 0, 0));
        LineTerminalView back = roundTrip(new LineTerminalView(0, false, "", List.of(), 0, 8, buses, many));
        assertEquals(
            LineTerminalView.MAX_DEVICES,
            back.devices()
                .size());
        assertEquals(
            LineTerminalView.MAX_BUSES,
            back.buses()
                .size());
    }

    @Test
    void anEmptyViewRoundTrips() {
        assertEquals(LineTerminalView.EMPTY, roundTrip(LineTerminalView.EMPTY));
    }
}
