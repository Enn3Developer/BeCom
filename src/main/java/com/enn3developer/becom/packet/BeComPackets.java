package com.enn3developer.becom.packet;

import net.minecraft.entity.player.EntityPlayerMP;

import com.enn3developer.becom.BeCom;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

/**
 * BeCom's own network channel. Every message here goes from the server to clients; clients act on the server only
 * through ModularUI's synced actions, which the server checks (DESIGN.md 14: client input).
 */
public final class BeComPackets {

    public static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(BeCom.MODID);

    private BeComPackets() {}

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(LevelPacket.Handler.class, LevelPacket.class, id++, Side.CLIENT);
        CHANNEL.registerMessage(TonePacket.Handler.class, TonePacket.class, id++, Side.CLIENT);
        CHANNEL.registerMessage(WireSyncPacket.Handler.class, WireSyncPacket.class, id++, Side.CLIENT);
    }

    public static void send(IMessage message, EntityPlayerMP player) {
        CHANNEL.sendTo(message, player);
    }

    public static void sendToDimension(IMessage message, int dimension) {
        CHANNEL.sendToDimension(message, dimension);
    }
}
