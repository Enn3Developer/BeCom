package com.enn3developer.becom.packet;

import net.minecraft.entity.player.EntityPlayerMP;

import com.enn3developer.becom.logic.Tunables;
import com.enn3developer.becom.server.BeComServer;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * One rear-camera picture from this player's client (DESIGN.md 10.1): their view just before the HUD, shrunk and
 * compressed to JPEG. The server caps each client's pictures by size and rate (DESIGN.md 14: client input), and sends
 * them on only where a level allows.
 */
public final class RearUploadPacket implements IMessage {

    private byte[] jpeg = new byte[0];

    public RearUploadPacket() {}

    public RearUploadPacket(byte[] jpeg) {
        this.jpeg = jpeg;
    }

    public byte[] jpeg() {
        return jpeg;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        Wire.writeBytes(buf, jpeg);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        jpeg = Wire.readBytes(buf, Tunables.MAX_REAR_BYTES);
    }

    public static final class Handler implements IMessageHandler<RearUploadPacket, IMessage> {

        @Override
        public IMessage onMessage(RearUploadPacket message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null || message.jpeg.length == 0) return null;
            // Whichever thread this runs on, video is only touched on the server thread.
            BeComServer.later(() -> {
                BeComServer becom = BeComServer.get();
                if (becom != null) becom.video.rearFrame(player, message.jpeg);
            });
            return null;
        }
    }
}
