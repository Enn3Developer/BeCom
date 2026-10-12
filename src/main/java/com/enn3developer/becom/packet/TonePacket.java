package com.enn3developer.becom.packet;

import com.enn3developer.becom.BeCom;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** Which call tone the player's handset plays now (DESIGN.md 10, 15.2). Only that player hears it. */
public final class TonePacket implements IMessage {

    /** Saved by ordinal on the wire, so constants are only ever appended. */
    public enum Tone {

        NONE,
        DIAL,
        RINGBACK,
        BUSY,
        UNREACHABLE,
        HANG_UP;

        private static final Tone[] VALUES = values();

        public static Tone byOrdinal(int ordinal) {
            return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : NONE;
        }
    }

    private Tone tone;

    public TonePacket() {}

    public TonePacket(Tone tone) {
        this.tone = tone;
    }

    public Tone tone() {
        return tone;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        tone = Tone.byOrdinal(buf.readByte());
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(tone.ordinal());
    }

    public static final class Handler implements IMessageHandler<TonePacket, IMessage> {

        @Override
        public IMessage onMessage(TonePacket message, MessageContext ctx) {
            BeCom.proxy.onTone(message);
            return null;
        }
    }
}
