package com.enn3developer.becom.packet;

import com.enn3developer.becom.BeCom;
import com.enn3developer.becom.logic.SignalLevel;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Whether the player is in a call, and the weakest level they hear it at, for the signal bars by the hotbar.
 * The client can't tell this from the levels it gets: those also cover a Videophone's loudspeaker it stands
 * near. Sent when it changes.
 */
public final class CallBarsPacket implements IMessage {

    private boolean inCall;
    private SignalLevel level = SignalLevel.NONE;

    public CallBarsPacket() {}

    public CallBarsPacket(boolean inCall, SignalLevel level) {
        this.inCall = inCall;
        this.level = level;
    }

    public boolean inCall() {
        return inCall;
    }

    public SignalLevel level() {
        return level;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        inCall = buf.readBoolean();
        level = SignalLevel.byOrdinal(buf.readByte());
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(inCall);
        buf.writeByte(level.ordinal());
    }

    public static final class Handler implements IMessageHandler<CallBarsPacket, IMessage> {

        @Override
        public IMessage onMessage(CallBarsPacket message, MessageContext ctx) {
            BeCom.proxy.onCallBars(message);
            return null;
        }
    }
}
