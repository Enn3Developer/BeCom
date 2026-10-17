package com.enn3developer.becom.packet;

import com.enn3developer.becom.BeCom;
import com.enn3developer.becom.logic.SignalLevel;
import com.enn3developer.becom.logic.radio.RadioModel;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * A Signal Analyzer reading on each band: radios on VHF, phones on UHF, where weather counts.
 * Sent once a refresh period while the player holds an analyzer. What serves the player best is an antenna or
 * the satellites over their dimension (9.1).
 */
public final class AnalyzerPacket implements IMessage {

    /**
     * One band: the antenna serving the player best, or the satellites ({@code satellites} above 0, how many orbit),
     * every loss on that link and the level; or nothing in reach.
     */
    public record Reading(boolean served, int satellites, int x, int y, int z, int tier, double budget, double distance,
        double terrain, double cover, double weather, double noise, SignalLevel level) {

        public static final Reading NONE = new Reading(false, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, SignalLevel.NONE);

        public static Reading of(int x, int y, int z, int tier, RadioModel.Losses losses, SignalLevel level) {
            return new Reading(
                true,
                0,
                x,
                y,
                z,
                tier,
                losses.budget(),
                losses.distance(),
                losses.terrain(),
                losses.coverA() + losses.coverB(),
                losses.weather(),
                losses.noise(),
                level);
        }

        /** The satellites serving the player: {@code satellites} of them, and the hop's terms. */
        public static Reading sky(int satellites, RadioModel.Losses losses, SignalLevel level) {
            return new Reading(
                true,
                satellites,
                0,
                0,
                0,
                0,
                losses.budget(),
                losses.distance(),
                losses.terrain(),
                losses.coverA() + losses.coverB(),
                losses.weather(),
                losses.noise(),
                level);
        }

        /** Whether the satellites serve the player, rather than an antenna. */
        public boolean sky() {
            return satellites > 0;
        }

        /** Every term again, as the model keeps them; cover counts both ends as one. */
        public RadioModel.Losses losses() {
            return new RadioModel.Losses(budget, distance, terrain, cover, 0, weather, noise);
        }

        static Reading read(ByteBuf buf) {
            if (!buf.readBoolean()) return NONE;
            return new Reading(
                true,
                buf.readInt(),
                buf.readInt(),
                buf.readShort(),
                buf.readInt(),
                buf.readByte(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat(),
                SignalLevel.byOrdinal(buf.readByte()));
        }

        void write(ByteBuf buf) {
            buf.writeBoolean(served);
            if (!served) return;
            buf.writeInt(satellites);
            buf.writeInt(x);
            buf.writeShort(y);
            buf.writeInt(z);
            buf.writeByte(tier);
            buf.writeFloat((float) budget);
            buf.writeFloat((float) distance);
            buf.writeFloat((float) terrain);
            buf.writeFloat((float) cover);
            buf.writeFloat((float) weather);
            buf.writeFloat((float) noise);
            buf.writeByte(level.ordinal());
        }
    }

    private Reading radio = Reading.NONE;
    private Reading phone = Reading.NONE;

    public AnalyzerPacket() {}

    public AnalyzerPacket(Reading radio, Reading phone) {
        this.radio = radio;
        this.phone = phone;
    }

    /** What a radio gets where the player stands, on VHF. */
    public Reading radio() {
        return radio;
    }

    /** What a Mobile Phone gets where the player stands, on UHF. */
    public Reading phone() {
        return phone;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        radio = Reading.read(buf);
        phone = Reading.read(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        radio.write(buf);
        phone.write(buf);
    }

    public static final class Handler implements IMessageHandler<AnalyzerPacket, IMessage> {

        @Override
        public IMessage onMessage(AnalyzerPacket message, MessageContext ctx) {
            BeCom.proxy.onAnalyzer(message);
            return null;
        }
    }
}
