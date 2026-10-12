package com.enn3developer.becom.gui;

import net.minecraft.network.PacketBuffer;

import com.enn3developer.becom.logic.SignalLevel;

/**
 * What the Satellite Dish's screen shows (DESIGN.md 9.2): the satellites over its dimension, its hop up to them and
 * that hop's level, its maintenance problems and what they cost, and what it's wrench-linked to. Built on the server,
 * translated on the client.
 *
 * @param sky           whether open sky is over the whole dish
 * @param enough        whether its dimension has satellites enough for sky hops
 * @param satellitesLow how many sky hops need
 * @param linked        where its wrench link goes, or empty
 */
public record DishView(boolean formed, int satellites, boolean sky, boolean enough, int satellitesLow, double snr,
    SignalLevel level, int problems, double problemLoss, String linked) {

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
        AntennaView.writeString(buf, linked);
    }

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
            AntennaView.readString(buf));
    }
}
