package com.enn3developer.becom.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.PacketBuffer;

/**
 * What the Exchange's screen shows, from the Router board: whether it works; its tier; its calls, its
 * data and how many numbers it reaches through how many exchanges; its racks, slice by slice; and a row for each of
 * its Line Hatches, with the bus of cable at its front. Built on the server.
 *
 * @param tier  its casings' tier, 1 LV to 3 HV; 0 when it isn't formed
 * @param calls the calls through it now
 * @param slots the calls its racks switch at once
 * @param racks its rack slots, slice by slice from the front: each slice's left wall top and bottom, then its right
 *              wall's, seen from the front; a rack's tier ordinal, or -1 for a free slot
 * @param why   why it isn't formed, as a lang key and its arguments; empty when it is
 */
public record ExchangeView(boolean online, boolean formed, int tier, int calls, int slots, int data, int routes,
    int reaches, int exchanges, List<Integer> racks, List<Line> lines, List<String> why) {

    /**
     * One of its Line Hatches.
     *
     * @param hatch the hatch's tier ordinal, which caps its cable
     * @param bus   the bus of the cable at its front, as the Router's screen shows one; null with none joined there
     */
    public record Line(int hatch, RouterView.Bus bus) {}

    public static final ExchangeView EMPTY = new ExchangeView(
        false,
        false,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        List.of(),
        List.of(),
        List.of());

    // A whole hall's slots; more lines than a screen pages through. Both bound what a client reads.
    public static final int MAX_RACKS = 32;
    public static final int MAX_LINES = 64;
    private static final int MAX_WHY = 4;

    public void write(PacketBuffer buf) {
        buf.writeBoolean(online);
        buf.writeBoolean(formed);
        buf.writeByte(tier);
        buf.writeInt(calls);
        buf.writeInt(slots);
        buf.writeInt(data);
        buf.writeInt(routes);
        buf.writeInt(reaches);
        buf.writeInt(exchanges);
        int rackCount = Math.min(racks.size(), MAX_RACKS);
        buf.writeByte(rackCount);
        for (int i = 0; i < rackCount; i++) buf.writeByte(racks.get(i));
        int count = Math.min(lines.size(), MAX_LINES);
        buf.writeByte(count);
        for (int i = 0; i < count; i++) {
            Line line = lines.get(i);
            buf.writeByte(line.hatch());
            buf.writeBoolean(line.bus() != null);
            if (line.bus() != null) RouterView.writeBus(buf, line.bus());
        }
        int whyCount = Math.min(why.size(), MAX_WHY);
        buf.writeByte(whyCount);
        for (int i = 0; i < whyCount; i++) AntennaView.writeString(buf, why.get(i));
    }

    public static ExchangeView read(PacketBuffer buf) {
        boolean online = buf.readBoolean();
        boolean formed = buf.readBoolean();
        int tier = buf.readByte();
        int calls = buf.readInt();
        int slots = buf.readInt();
        int data = buf.readInt();
        int routes = buf.readInt();
        int reaches = buf.readInt();
        int exchanges = buf.readInt();
        int rackCount = Math.min(buf.readUnsignedByte(), MAX_RACKS);
        List<Integer> racks = new ArrayList<>(rackCount);
        for (int i = 0; i < rackCount; i++) racks.add((int) buf.readByte());
        int count = Math.min(buf.readUnsignedByte(), MAX_LINES);
        List<Line> lines = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int hatch = buf.readByte();
            lines.add(new Line(hatch, buf.readBoolean() ? RouterView.readBus(buf) : null));
        }
        int whyCount = Math.min(buf.readUnsignedByte(), MAX_WHY);
        List<String> why = new ArrayList<>(whyCount);
        for (int i = 0; i < whyCount; i++) why.add(AntennaView.readString(buf));
        return new ExchangeView(
            online,
            formed,
            tier,
            calls,
            slots,
            data,
            routes,
            reaches,
            exchanges,
            racks,
            lines,
            why);
    }
}
