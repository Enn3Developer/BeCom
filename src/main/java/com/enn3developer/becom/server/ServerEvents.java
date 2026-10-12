package com.enn3developer.becom.server;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.world.WorldEvent;

import com.enn3developer.becom.quest.BeComQuests;
import com.enn3developer.becom.selftest.SelfTest;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** World, player and tick events BeCom follows on the server. */
public final class ServerEvents {

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        BeComServer becom = BeComServer.get();
        if (becom == null) return;
        becom.tick();
        BeComQuests.backend()
            .tick();
        SelfTest.tick();
    }

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        BeComServer becom = BeComServer.get();
        if (becom != null && !event.world.isRemote) becom.network.worldLoaded(event.world);
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        BeComServer becom = BeComServer.get();
        if (becom != null && !event.world.isRemote) becom.network.worldUnloaded(event.world);
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        BeComServer becom = BeComServer.get();
        if (becom != null && event.player instanceof EntityPlayerMP player) becom.network.sendWires(player);
    }

    @SubscribeEvent
    public void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        BeComServer becom = BeComServer.get();
        if (becom != null && event.player instanceof EntityPlayerMP player) becom.network.sendWires(player);
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        BeComServer becom = BeComServer.get();
        if (becom == null) return;
        becom.calls.logout(event.player.getUniqueID());
        becom.links.forget(event.player.getUniqueID());
    }
}
