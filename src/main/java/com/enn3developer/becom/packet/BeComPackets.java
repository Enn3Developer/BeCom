package com.enn3developer.becom.packet;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.common.util.FakePlayer;

import com.enn3developer.becom.BeCom;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

/**
 * BeCom's own network channel. Messages go from the server to clients, but for sneak + scroll on a walkie-talkie, the
 * Phone key and rear-camera pictures; otherwise clients act on the server only through ModularUI's synced actions. The
 * server checks all of them. Video travels here too.
 */
public final class BeComPackets {

    public static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(BeCom.MODID);

    private BeComPackets() {}

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(LevelPacket.Handler.class, LevelPacket.class, id++, Side.CLIENT);
        CHANNEL.registerMessage(TonePacket.Handler.class, TonePacket.class, id++, Side.CLIENT);
        CHANNEL.registerMessage(WireSyncPacket.Handler.class, WireSyncPacket.class, id++, Side.CLIENT);
        CHANNEL.registerMessage(ConfigSyncPacket.Handler.class, ConfigSyncPacket.class, id++, Side.CLIENT);
        CHANNEL.registerMessage(AnalyzerPacket.Handler.class, AnalyzerPacket.class, id++, Side.CLIENT);
        CHANNEL.registerMessage(WalkieChannelPacket.Handler.class, WalkieChannelPacket.class, id++, Side.SERVER);
        CHANNEL.registerMessage(RingPacket.Handler.class, RingPacket.class, id++, Side.CLIENT);
        CHANNEL.registerMessage(PhoneKeyPacket.Handler.class, PhoneKeyPacket.class, id++, Side.SERVER);
        CHANNEL.registerMessage(PhoneStatusPacket.Handler.class, PhoneStatusPacket.class, id++, Side.CLIENT);
        CHANNEL.registerMessage(VideoStatePacket.Handler.class, VideoStatePacket.class, id++, Side.CLIENT);
        CHANNEL.registerMessage(VideoPosePacket.Handler.class, VideoPosePacket.class, id++, Side.CLIENT);
        CHANNEL.registerMessage(VideoEquipmentPacket.Handler.class, VideoEquipmentPacket.class, id++, Side.CLIENT);
        CHANNEL.registerMessage(VideoProfilePacket.Handler.class, VideoProfilePacket.class, id++, Side.CLIENT);
        CHANNEL.registerMessage(RearFramePacket.Handler.class, RearFramePacket.class, id++, Side.CLIENT);
        CHANNEL.registerMessage(RearUploadPacket.Handler.class, RearUploadPacket.class, id++, Side.SERVER);
        CHANNEL.registerMessage(CallBarsPacket.Handler.class, CallBarsPacket.class, id++, Side.CLIENT);
    }

    public static void send(IMessage message, EntityPlayerMP player) {
        // A fake player (a machine using an item or block) has no connection to send to.
        if (player instanceof FakePlayer || player.playerNetServerHandler == null) return;
        CHANNEL.sendTo(message, player);
    }

    public static void sendToDimension(IMessage message, int dimension) {
        CHANNEL.sendToDimension(message, dimension);
    }
}
