package com.enn3developer.becom.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.PacketBuffer;

/**
 * What the Line Terminal's screen shows: its state, its uplink and how much of it calls take, the calls it carries,
 * each bus of cable joining it, and what's on those buses, with what each device is doing now. Built on the server,
 * formatted on the client.
 *
 * @param tier    the terminal's cable tier ordinal, which names its voltage
 * @param calls   the calls through it now
 * @param slots   the calls it carries at once
 * @param buses   the buses joining it, by id
 * @param devices what's on those buses: phones by number, then routers and gateways
 */
public record LineTerminalView(int tier, boolean working, String owner, List<Uplink> uplinks, int calls, int slots,
    List<Bus> buses, List<Device> devices) {

    public static final int MAX_UPLINKS = 4;
    public static final int MAX_BUSES = 6;
    public static final int MAX_DEVICES = 64;

    public static final LineTerminalView EMPTY = new LineTerminalView(
        0,
        false,
        "",
        List.of(),
        0,
        0,
        List.of(),
        List.of());

    /**
     * Where its link goes and what the run carries: the node at the far end ({@code kind} a NodeKind ordinal, -1 for an
     * unpowered pylon), its span's cables as counts per tier, and the run's KB/s, how many calls take of it and how
     * many data does.
     */
    public record Uplink(int kind, int x, int y, int z, int distance, int[] cables, int rate, int used, int data) {}

    /**
     * A bus joining it: its cable's tier ordinal, its cable blocks and the most its tier carries over, its KB/s
     * (0 when it carries nothing), what calls and data take of it, and how many phones, other routers and gateways it
     * reaches.
     */
    public record Bus(int cable, int cables, int limit, int rate, int used, int data, int phones, int routers,
        int gateways) {

        public boolean tooLong() {
            return cables > limit;
        }
    }

    /** Ordinals go on the wire, so constants are only ever appended. */
    public enum Now {

        IDLE,
        CALL,
        RINGING,
        /** On a bus too long to carry. */
        TOO_LONG,
        /** A router or a gateway on its bus: no call of its own. */
        JOINED;

        private static final Now[] VALUES = values();

        static Now byOrdinal(int ordinal) {
            return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : IDLE;
        }
    }

    /**
     * Something on its buses: a phone ({@code kind} a PhoneKind ordinal, -1 for an unknown one) by number (-1 without
     * one), or a router or gateway ({@code node} a NodeKind ordinal, -1 for a phone); which of its buses it's on, by
     * index; and what it's doing: {@code with} who's on the other side of a call.
     */
    public record Device(int kind, int node, int number, int bus, Now now, String with) {}

    public void write(PacketBuffer buf) {
        buf.writeByte(tier);
        buf.writeBoolean(working);
        AntennaView.writeString(buf, owner);
        int count = Math.min(uplinks.size(), MAX_UPLINKS);
        buf.writeByte(count);
        for (int i = 0; i < count; i++) {
            Uplink u = uplinks.get(i);
            buf.writeByte(u.kind());
            buf.writeInt(u.x());
            buf.writeShort(u.y());
            buf.writeInt(u.z());
            buf.writeInt(u.distance());
            buf.writeByte(u.cables().length);
            for (int c : u.cables()) buf.writeByte(c);
            buf.writeInt(u.rate());
            buf.writeInt(u.used());
            buf.writeInt(u.data());
        }
        buf.writeInt(calls);
        buf.writeInt(slots);
        count = Math.min(buses.size(), MAX_BUSES);
        buf.writeByte(count);
        for (int i = 0; i < count; i++) {
            Bus b = buses.get(i);
            buf.writeByte(b.cable());
            buf.writeInt(b.cables());
            buf.writeInt(b.limit());
            buf.writeInt(b.rate());
            buf.writeInt(b.used());
            buf.writeInt(b.data());
            buf.writeShort(b.phones());
            buf.writeShort(b.routers());
            buf.writeShort(b.gateways());
        }
        count = Math.min(devices.size(), MAX_DEVICES);
        buf.writeShort(count);
        for (int i = 0; i < count; i++) {
            Device d = devices.get(i);
            buf.writeByte(d.kind());
            buf.writeByte(d.node());
            buf.writeInt(d.number());
            buf.writeByte(d.bus());
            buf.writeByte(
                d.now()
                    .ordinal());
            AntennaView.writeString(buf, d.with());
        }
    }

    public static LineTerminalView read(PacketBuffer buf) {
        int tier = buf.readByte();
        boolean working = buf.readBoolean();
        String owner = AntennaView.readString(buf);
        int count = Math.min(buf.readUnsignedByte(), MAX_UPLINKS);
        List<Uplink> uplinks = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int kind = buf.readByte();
            int x = buf.readInt();
            int y = buf.readShort();
            int z = buf.readInt();
            int distance = buf.readInt();
            int[] cables = new int[Math.min(buf.readUnsignedByte(), 8)];
            for (int c = 0; c < cables.length; c++) cables[c] = buf.readByte();
            uplinks.add(new Uplink(kind, x, y, z, distance, cables, buf.readInt(), buf.readInt(), buf.readInt()));
        }
        int calls = buf.readInt();
        int slots = buf.readInt();
        count = Math.min(buf.readUnsignedByte(), MAX_BUSES);
        List<Bus> buses = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            buses.add(
                new Bus(
                    buf.readByte(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readShort(),
                    buf.readShort(),
                    buf.readShort()));
        }
        count = Math.min(buf.readShort(), MAX_DEVICES);
        List<Device> devices = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            devices.add(
                new Device(
                    buf.readByte(),
                    buf.readByte(),
                    buf.readInt(),
                    buf.readByte(),
                    Now.byOrdinal(buf.readByte()),
                    AntennaView.readString(buf)));
        }
        return new LineTerminalView(tier, working, owner, uplinks, calls, slots, buses, devices);
    }
}
