package com.enn3developer.becom.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.PacketBuffer;

/**
 * What the Line Terminal's screen shows: its state, its uplink and how much of it calls take, and each device on its
 * cable, with what it's doing now or why it has no line. Built on the server, formatted on the client.
 *
 * @param tier    the terminal's cable tier ordinal, which names its voltage
 * @param ports   how many devices it gives a line
 * @param devices the served ones first, nearest first, then the ones it can't serve
 */
public record LineTerminalView(int tier, boolean working, String owner, List<Uplink> uplinks, int ports,
    List<Device> devices) {

    public static final int MAX_UPLINKS = 4;
    public static final int MAX_DEVICES = 64;

    public static final LineTerminalView EMPTY = new LineTerminalView(0, false, "", List.of(), 0, List.of());

    /**
     * Where its link goes and what the run carries: the node at the far end ({@code kind} a NodeKind ordinal, -1 for an
     * unpowered pylon), its span's cables as counts per tier, and the run's KB/s, how many calls take of it and how
     * many data does.
     */
    public record Uplink(int kind, int x, int y, int z, int distance, int[] cables, int rate, int used, int data) {}

    /** Ordinals go on the wire, so constants are only ever appended. */
    public enum Now {

        IDLE,
        CALL,
        RINGING,
        NO_PORT,
        TOO_FAR,
        NEARER;

        private static final Now[] VALUES = values();

        static Now byOrdinal(int ordinal) {
            return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : IDLE;
        }

        /** Whether this terminal gives the device a line. */
        public boolean served() {
            return this == IDLE || this == CALL || this == RINGING;
        }
    }

    /**
     * A device on its cable: {@code kind} a PhoneKind ordinal or -1, its number (-1 without one), its cable's tier
     * ordinal, its run and that cable's reach in blocks, and what it's doing: {@code with} the other side of a call.
     */
    public record Device(int kind, int number, int cable, int run, int reach, Now now, String with) {}

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
        buf.writeShort(ports);
        count = Math.min(devices.size(), MAX_DEVICES);
        buf.writeShort(count);
        for (int i = 0; i < count; i++) {
            Device d = devices.get(i);
            buf.writeByte(d.kind());
            buf.writeInt(d.number());
            buf.writeByte(d.cable());
            buf.writeInt(d.run());
            buf.writeInt(d.reach());
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
        int ports = buf.readShort();
        count = Math.min(buf.readShort(), MAX_DEVICES);
        List<Device> devices = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            devices.add(
                new Device(
                    buf.readByte(),
                    buf.readInt(),
                    buf.readByte(),
                    buf.readInt(),
                    buf.readInt(),
                    Now.byOrdinal(buf.readByte()),
                    AntennaView.readString(buf)));
        }
        return new LineTerminalView(tier, working, owner, uplinks, ports, devices);
    }
}
