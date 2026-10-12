package com.enn3developer.becom.packet;

import net.minecraft.entity.player.EntityPlayerMP;

import com.enn3developer.becom.server.BeComServer;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * The Phone key (DESIGN.md 11.1, D34): answers a ringing phone, hangs up during a call, opens the phone screen
 * otherwise; with sneak, rejects a ringing call. The server decides which, from its own state.
 */
public final class PhoneKeyPacket implements IMessage {

    private boolean sneaking;

    public PhoneKeyPacket() {}

    public PhoneKeyPacket(boolean sneaking) {
        this.sneaking = sneaking;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        sneaking = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(sneaking);
    }

    public static final class Handler implements IMessageHandler<PhoneKeyPacket, IMessage> {

        @Override
        public IMessage onMessage(PhoneKeyPacket message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;
            // Whichever thread this runs on, calls are only touched on the server thread.
            BeComServer.later(() -> {
                BeComServer becom = BeComServer.get();
                if (becom != null) becom.calls.phoneKey(player, message.sneaking);
            });
            return null;
        }
    }
}
