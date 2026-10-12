package com.enn3developer.becom.packet;

import com.enn3developer.becom.BeCom;
import com.enn3developer.becom.logic.SignalLevel;
import com.enn3developer.becom.logic.radio.RadioModel;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * A Signal Analyzer reading (DESIGN.md 6.6, 11.4): the antenna serving the player best and every loss on that link, or
 * nothing in reach. Sent once a refresh period while the player holds an analyzer.
 */
public final class AnalyzerPacket implements IMessage {

    private boolean served;
    private int x;
    private int y;
    private int z;
    private int tier;
    private double budget;
    private double distanceLoss;
    private double terrain;
    private double cover;
    private double weather;
    private double noise;
    private SignalLevel level = SignalLevel.NONE;

    public AnalyzerPacket() {}

    /** Nothing reaches the player. */
    public static AnalyzerPacket none() {
        return new AnalyzerPacket();
    }

    public AnalyzerPacket(int x, int y, int z, int tier, RadioModel.Losses losses, SignalLevel level) {
        this.served = true;
        this.x = x;
        this.y = y;
        this.z = z;
        this.tier = tier;
        this.budget = losses.budget();
        this.distanceLoss = losses.distance();
        this.terrain = losses.terrain();
        this.cover = losses.coverA() + losses.coverB();
        this.weather = losses.weather();
        this.noise = losses.noise();
        this.level = level;
    }

    public boolean served() {
        return served;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int z() {
        return z;
    }

    public int tier() {
        return tier;
    }

    public SignalLevel level() {
        return level;
    }

    /** Every term again, as the model keeps them; cover counts both ends as one. */
    public RadioModel.Losses losses() {
        return new RadioModel.Losses(budget, distanceLoss, terrain, cover, 0, weather, noise);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        served = buf.readBoolean();
        if (!served) return;
        x = buf.readInt();
        y = buf.readShort();
        z = buf.readInt();
        tier = buf.readByte();
        budget = buf.readFloat();
        distanceLoss = buf.readFloat();
        terrain = buf.readFloat();
        cover = buf.readFloat();
        weather = buf.readFloat();
        noise = buf.readFloat();
        level = SignalLevel.byOrdinal(buf.readByte());
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(served);
        if (!served) return;
        buf.writeInt(x);
        buf.writeShort(y);
        buf.writeInt(z);
        buf.writeByte(tier);
        buf.writeFloat((float) budget);
        buf.writeFloat((float) distanceLoss);
        buf.writeFloat((float) terrain);
        buf.writeFloat((float) cover);
        buf.writeFloat((float) weather);
        buf.writeFloat((float) noise);
        buf.writeByte(level.ordinal());
    }

    public static final class Handler implements IMessageHandler<AnalyzerPacket, IMessage> {

        @Override
        public IMessage onMessage(AnalyzerPacket message, MessageContext ctx) {
            BeCom.proxy.onAnalyzer(message);
            return null;
        }
    }
}
