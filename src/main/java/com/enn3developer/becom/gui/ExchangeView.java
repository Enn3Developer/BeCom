package com.enn3developer.becom.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.PacketBuffer;

/**
 * What the Exchange's screen shows, from the Router board: whether it works and whose it is; its calls, its data and
 * how many numbers it reaches through how many exchanges; its switching racks; and a row for each thing joined to it,
 * by a run of wire over pylons or on a bus of its own cable, with the cables that join it and what they carry. Built
 * on the server.
 *
 * @param racks each rack's tier, best first
 */
public record ExchangeView(boolean online, boolean formed, String owner, int x, int y, int z, int calls, int slots,
    int data, int routes, int reaches, int exchanges, List<Integer> racks, List<Joined> joined) {

    /**
     * One thing joined to the Exchange.
     *
     * @param node      its node kind's ordinal, or -1 for a phone on one of the Exchange's buses
     * @param phoneKind that phone's kind's ordinal, or -1
     * @param cables    how many cables of each tier join it, by tier
     * @param length    the blocks of wire to it: a run's length, or the cable blocks of the bus it's on
     * @param rate      the KB/s the wire carries; 0 when it carries nothing
     * @param used      the KB/s calls and data take of it now, on a bus shared by everything on it
     */
    public record Joined(int node, int phoneKind, int number, int x, int y, int z, List<Integer> cables, int length,
        int rate, int used) {}

    public static final ExchangeView EMPTY = new ExchangeView(
        false,
        false,
        "",
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        List.of(),
        List.of());

    // More than a screen pages through; it also bounds what a client reads.
    public static final int MAX_JOINED = 64;
    private static final int MAX_RACKS = 16;
    private static final int MAX_TIERS = 8;

    public void write(PacketBuffer buf) {
        buf.writeBoolean(online);
        buf.writeBoolean(formed);
        AntennaView.writeString(buf, owner);
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
        buf.writeInt(calls);
        buf.writeInt(slots);
        buf.writeInt(data);
        buf.writeInt(routes);
        buf.writeInt(reaches);
        buf.writeInt(exchanges);
        int rackCount = Math.min(racks.size(), MAX_RACKS);
        buf.writeByte(rackCount);
        for (int i = 0; i < rackCount; i++) buf.writeByte(racks.get(i));
        int count = Math.min(joined.size(), MAX_JOINED);
        buf.writeByte(count);
        for (int i = 0; i < count; i++) {
            Joined j = joined.get(i);
            buf.writeByte(j.node());
            buf.writeByte(j.phoneKind());
            buf.writeInt(j.number());
            buf.writeInt(j.x());
            buf.writeInt(j.y());
            buf.writeInt(j.z());
            int tiers = Math.min(
                j.cables()
                    .size(),
                MAX_TIERS);
            buf.writeByte(tiers);
            for (int t = 0; t < tiers; t++) buf.writeByte(
                j.cables()
                    .get(t));
            buf.writeInt(j.length());
            buf.writeInt(j.rate());
            buf.writeInt(j.used());
        }
    }

    public static ExchangeView read(PacketBuffer buf) {
        boolean online = buf.readBoolean();
        boolean formed = buf.readBoolean();
        String owner = AntennaView.readString(buf);
        int x = buf.readInt();
        int y = buf.readInt();
        int z = buf.readInt();
        int calls = buf.readInt();
        int slots = buf.readInt();
        int data = buf.readInt();
        int routes = buf.readInt();
        int reaches = buf.readInt();
        int exchanges = buf.readInt();
        int rackCount = Math.min(buf.readUnsignedByte(), MAX_RACKS);
        List<Integer> racks = new ArrayList<>(rackCount);
        for (int i = 0; i < rackCount; i++) racks.add((int) buf.readByte());
        int count = Math.min(buf.readUnsignedByte(), MAX_JOINED);
        List<Joined> joined = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int node = buf.readByte();
            int phoneKind = buf.readByte();
            int number = buf.readInt();
            int jx = buf.readInt();
            int jy = buf.readInt();
            int jz = buf.readInt();
            int tiers = Math.min(buf.readUnsignedByte(), MAX_TIERS);
            List<Integer> cables = new ArrayList<>(tiers);
            for (int t = 0; t < tiers; t++) cables.add((int) buf.readByte());
            joined.add(
                new Joined(node, phoneKind, number, jx, jy, jz, cables, buf.readInt(), buf.readInt(), buf.readInt()));
        }
        return new ExchangeView(
            online,
            formed,
            owner,
            x,
            y,
            z,
            calls,
            slots,
            data,
            routes,
            reaches,
            exchanges,
            racks,
            joined);
    }
}
