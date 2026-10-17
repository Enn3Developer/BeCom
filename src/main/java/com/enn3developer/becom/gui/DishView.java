package com.enn3developer.becom.gui;

import net.minecraft.network.PacketBuffer;

import com.enn3developer.becom.logic.SignalLevel;

/**
 * What the Satellite Dish's screen shows: the satellites over its dimension, its hop up to them and
 * that hop's level, its maintenance problems and what they cost, and the server's lines on its calls and backhaul.
 * Built on the server,
 * translated on the client.
 *
 * @param sky           whether open sky is over the whole dish
 * @param enough        whether its dimension has satellites enough for sky hops
 * @param satellitesLow how many sky hops need
 * @param more          the server's lines on its calls, the sky's and its backhaul, encoded as {@code InfoLines} does
 */
public record DishView(boolean formed, int satellites, boolean sky, boolean enough, int satellitesLow, double snr,
    SignalLevel level, int problems, double problemLoss, String more) {

    public static final DishView EMPTY = new DishView(false, 0, false, false, 0, 0, SignalLevel.NONE, 0, 0, "");

    public void write(PacketBuffer buf) {
        buf.writeBoolean(formed);
        buf.writeInt(satellites);
        buf.writeBoolean(sky);
        buf.writeBoolean(enough);
        buf.writeInt(satellitesLow);
        buf.writeDouble(snr);
        buf.writeByte(level.ordinal());
        buf.writeByte(problems);
        buf.writeDouble(problemLoss);
        byte[] bytes = more.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        buf.writeShort(Math.min(bytes.length, MAX_MORE));
        buf.writeBytes(bytes, 0, Math.min(bytes.length, MAX_MORE));
    }

    // Whole lines, as InfoScreen.fit leaves them.
    private static final int MAX_MORE = 16 * 1024;

    public static DishView read(PacketBuffer buf) {
        return new DishView(
            buf.readBoolean(),
            buf.readInt(),
            buf.readBoolean(),
            buf.readBoolean(),
            buf.readInt(),
            buf.readDouble(),
            SignalLevel.byOrdinal(buf.readByte()),
            buf.readByte(),
            buf.readDouble(),
            readMore(buf));
    }

    private static String readMore(PacketBuffer buf) {
        byte[] bytes = new byte[Math.max(0, Math.min(buf.readShort(), MAX_MORE))];
        buf.readBytes(bytes);
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }
}
