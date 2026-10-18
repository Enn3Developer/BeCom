package com.enn3developer.becom.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.network.PacketBuffer;

import com.enn3developer.becom.logic.radio.Coverage;

/**
 * What the antenna screen shows: its state, the calls through it out of those it carries, its links
 * to other antennas on each band, its wiring onto lines and what each span carries, the team lock, and its coverage
 * map on each band. Built on the server, formatted on the client.
 */
public record AntennaView(int tier, boolean formed, boolean working, int mast, int range, boolean surveyed,
    boolean locked, boolean mayManage, String owner, int calls, int callSlots, List<Link> links, List<Bridge> bridges,
    int mapRange, byte[] vhfMap, byte[] uhfMap, int radioRate, int radioLevel) {

    // A dimension rarely has more antennas in reach than this; the screen lists the strongest.
    public static final int MAX_LINKS = 64;
    public static final int MAX_BRIDGES = 15;

    public static final AntennaView EMPTY = new AntennaView(
        0,
        false,
        false,
        0,
        0,
        false,
        false,
        false,
        "",
        0,
        0,
        Collections.emptyList(),
        Collections.emptyList(),
        0,
        new byte[0],
        new byte[0],
        0,
        0);

    private static final int MAP_SIZE = Coverage.CELLS * Coverage.CELLS;

    /** A link to another antenna: where, how far, and its SNR on each band (negative infinity below Low). */
    public record Link(int x, int y, int z, int distance, double vhf, double uhf) {}

    /**
     * Its backhaul: the working node its cable leads to (its NodeKind ordinal, -1 for none), where and how far, its
     * best
     * cable (a CableTier ordinal, -1 for none), the KB/s the run carries (-1 for none) and what calls take of it.
     */
    public record Bridge(int kind, int x, int y, int z, int distance, int cable, int rate, int used) {}

    public void write(PacketBuffer buf) {
        buf.writeByte(tier);
        buf.writeBoolean(formed);
        buf.writeBoolean(working);
        buf.writeShort(mast);
        buf.writeShort(range);
        buf.writeBoolean(surveyed);
        buf.writeBoolean(locked);
        buf.writeBoolean(mayManage);
        writeString(buf, owner);
        buf.writeShort(calls);
        buf.writeShort(callSlots);
        int links = Math.min(this.links.size(), MAX_LINKS);
        buf.writeShort(links);
        for (int i = 0; i < links; i++) {
            Link link = this.links.get(i);
            buf.writeInt(link.x);
            buf.writeShort(link.y);
            buf.writeInt(link.z);
            buf.writeInt(link.distance);
            buf.writeDouble(link.vhf);
            buf.writeDouble(link.uhf);
        }
        int bridges = Math.min(this.bridges.size(), MAX_BRIDGES);
        buf.writeShort(bridges);
        for (int i = 0; i < bridges; i++) {
            Bridge bridge = this.bridges.get(i);
            buf.writeInt(bridge.x);
            buf.writeShort(bridge.y);
            buf.writeInt(bridge.z);
            buf.writeByte(bridge.kind);
            buf.writeInt(bridge.distance);
            buf.writeByte(bridge.cable);
            buf.writeInt(bridge.rate);
            buf.writeInt(bridge.used);
        }
        buf.writeShort(mapRange);
        writeMap(buf, vhfMap);
        writeMap(buf, uhfMap);
        buf.writeInt(radioRate);
        buf.writeByte(radioLevel);
    }

    private static void writeMap(PacketBuffer buf, byte[] map) {
        int length = map == null ? 0 : Math.min(map.length, MAP_SIZE);
        buf.writeShort(length);
        if (length > 0) buf.writeBytes(map, 0, length);
    }

    private static byte[] readMap(PacketBuffer buf) {
        byte[] map = new byte[Math.max(0, Math.min(buf.readShort(), MAP_SIZE))];
        buf.readBytes(map);
        return map;
    }

    public static AntennaView read(PacketBuffer buf) {
        int tier = buf.readByte();
        boolean formed = buf.readBoolean();
        boolean working = buf.readBoolean();
        int mast = buf.readShort();
        int range = buf.readShort();
        boolean surveyed = buf.readBoolean();
        boolean locked = buf.readBoolean();
        boolean mayManage = buf.readBoolean();
        String owner = readString(buf);
        int calls = buf.readShort();
        int callSlots = buf.readShort();
        int count = Math.min(buf.readShort(), MAX_LINKS);
        List<Link> links = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            links.add(
                new Link(
                    buf.readInt(),
                    buf.readShort(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readDouble(),
                    buf.readDouble()));
        }
        count = Math.min(buf.readShort(), MAX_BRIDGES);
        List<Bridge> bridges = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int x = buf.readInt();
            int y = buf.readShort();
            int z = buf.readInt();
            int kind = buf.readByte();
            bridges.add(new Bridge(kind, x, y, z, buf.readInt(), buf.readByte(), buf.readInt(), buf.readInt()));
        }
        int mapRange = buf.readShort();
        byte[] vhfMap = readMap(buf);
        byte[] uhfMap = readMap(buf);
        int radioRate = buf.readInt();
        int radioLevel = Math
            .min(buf.readUnsignedByte(), com.enn3developer.becom.logic.SignalLevel.values().length - 1);
        return new AntennaView(
            tier,
            formed,
            working,
            mast,
            range,
            surveyed,
            locked,
            mayManage,
            owner,
            calls,
            callSlots,
            links,
            bridges,
            mapRange,
            vhfMap,
            uhfMap,
            radioRate,
            radioLevel);
    }

    private static final int MAX_NAME = 64;

    static void writeString(PacketBuffer buf, String s) {
        // A missing name is sent as none, rather than throwing in the middle of a screen's sync every tick.
        if (s == null) s = "";
        byte[] bytes = (s.length() > MAX_NAME ? s.substring(0, MAX_NAME) : s)
            .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        buf.writeShort(bytes.length);
        buf.writeBytes(bytes);
    }

    static String readString(PacketBuffer buf) {
        int length = Math.min(buf.readShort(), MAX_NAME * 4);
        byte[] bytes = new byte[Math.max(0, length)];
        buf.readBytes(bytes);
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }
}
