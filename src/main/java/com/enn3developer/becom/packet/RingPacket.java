package com.enn3developer.becom.packet;

import java.nio.charset.StandardCharsets;

import com.enn3developer.becom.BeCom;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * A phone the player carries rings, or stopped ringing (DESIGN.md 11.2): the notice in the corner of the screen shows
 * who calls and from which number, with the keys to answer or reject.
 */
public final class RingPacket implements IMessage {

    // A player name is at most 16 characters; a longer one is cut rather than trusted.
    private static final int MAX_NAME_BYTES = 64;

    private boolean ringing;
    private String caller = "";
    private int number = -1;

    public RingPacket() {}

    private RingPacket(boolean ringing, String caller, int number) {
        this.ringing = ringing;
        this.caller = caller;
        this.number = number;
    }

    public static RingPacket ringing(String caller, int number) {
        return new RingPacket(true, caller, number);
    }

    public static RingPacket stop() {
        return new RingPacket(false, "", -1);
    }

    public boolean isRinging() {
        return ringing;
    }

    public String caller() {
        return caller;
    }

    public int number() {
        return number;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        ringing = buf.readBoolean();
        number = buf.readInt();
        int length = Math.min(buf.readUnsignedByte(), MAX_NAME_BYTES);
        byte[] bytes = new byte[length];
        buf.readBytes(bytes);
        caller = new String(bytes, StandardCharsets.UTF_8);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(ringing);
        buf.writeInt(number);
        byte[] bytes = caller.getBytes(StandardCharsets.UTF_8);
        int length = Math.min(bytes.length, MAX_NAME_BYTES);
        buf.writeByte(length);
        buf.writeBytes(bytes, 0, length);
    }

    public static final class Handler implements IMessageHandler<RingPacket, IMessage> {

        @Override
        public IMessage onMessage(RingPacket message, MessageContext ctx) {
            BeCom.proxy.onRing(message);
            return null;
        }
    }
}
