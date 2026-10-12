package com.enn3developer.becom.packet;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import com.enn3developer.becom.item.ItemWalkieTalkie;
import com.enn3developer.becom.server.BeComServer;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Sneak + scroll with a walkie-talkie in hand (DESIGN.md 8.1, 11.3): one channel up or down. The only message clients
 * send outside a screen; the server acts only on the walkie the sender holds.
 */
public final class WalkieChannelPacket implements IMessage {

    private int delta;

    public WalkieChannelPacket() {}

    public WalkieChannelPacket(int delta) {
        this.delta = Integer.signum(delta);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        delta = Integer.signum(buf.readByte());
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(delta);
    }

    public static final class Handler implements IMessageHandler<WalkieChannelPacket, IMessage> {

        @Override
        public IMessage onMessage(WalkieChannelPacket message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null || message.delta == 0) return null;
            // Whichever thread this runs on, the inventory is only touched on the server thread.
            BeComServer.later(() -> {
                ItemStack held = player.getHeldItem();
                if (!ItemWalkieTalkie.is(held)) return;
                ItemWalkieTalkie.setChannel(held, ItemWalkieTalkie.channel(held) + message.delta);
                player.inventory.markDirty();
            });
            return null;
        }
    }
}
