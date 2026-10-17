package com.enn3developer.becom.packet;

import java.util.UUID;

import com.enn3developer.becom.BeCom;
import com.enn3developer.becom.logic.Tunables;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * One rear-camera picture from the other person in a one-to-one call, a JPEG relayed by the server
 * as their client sent it, never decoded on the way.
 */
public final class RearFramePacket implements IMessage {

    private UUID source;
    private byte[] jpeg = new byte[0];

    public RearFramePacket() {}

    public RearFramePacket(UUID source, byte[] jpeg) {
        this.source = source;
        this.jpeg = jpeg;
    }

    public UUID source() {
        return source;
    }

    public byte[] jpeg() {
        return jpeg;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        Wire.writeUuid(buf, source);
        Wire.writeBytes(buf, jpeg);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        source = Wire.readUuid(buf);
        jpeg = Wire.readBytes(buf, Tunables.MAX_REAR_BYTES);
    }

    public static final class Handler implements IMessageHandler<RearFramePacket, IMessage> {

        @Override
        public IMessage onMessage(RearFramePacket message, MessageContext ctx) {
            BeCom.proxy.onRearFrame(message);
            return null;
        }
    }
}
