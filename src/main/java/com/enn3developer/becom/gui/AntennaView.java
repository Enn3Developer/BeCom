package com.enn3developer.becom.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.network.PacketBuffer;

import com.enn3developer.becom.logic.radio.Coverage;

/**
 * What the antenna screen shows (DESIGN.md 11.4): its state, its links to other antennas on each band, its bridges
 * onto wired lines, the team lock, and its coverage map on each band. Built on the server, formatted on the client.
 */
public record AntennaView(int tier, boolean formed, boolean working, int mast, int range, boolean surveyed,
    boolean locked, boolean mayManage, String owner, List<Link> links, List<Bridge> bridges, int mapRange,
    byte[] vhfMap, byte[] uhfMap) {

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
        Collections.emptyList(),
        Collections.emptyList(),
        0,
        new byte[0],
        new byte[0]);

    private static final int MAP_SIZE = Coverage.CELLS * Coverage.CELLS;

    /** A link to another antenna: where, how far, and its SNR on each band (negative infinity below Low). */
    public record Link(int x, int y, int z, int distance, double vhf, double uhf) {}

    /** A bridge onto a wired line: where, and how far. */
    public record Bridge(int x, int y, int z, int distance) {}

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
            buf.writeInt(bridge.distance);
        }
        buf.writeShort(mapRange);
        writeMap(buf, vhfMap);
        writeMap(buf, uhfMap);
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
            bridges.add(new Bridge(buf.readInt(), buf.readShort(), buf.readInt(), buf.readInt()));
        }
        int mapRange = buf.readShort();
        byte[] vhfMap = readMap(buf);
        byte[] uhfMap = readMap(buf);
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
            links,
            bridges,
            mapRange,
            vhfMap,
            uhfMap);
    }

    private static final int MAX_NAME = 64;

    static void writeString(PacketBuffer buf, String s) {
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
