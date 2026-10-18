package com.enn3developer.becom.server;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ChunkDataEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;

import com.enn3developer.becom.block.BlockPhoneCable;
import com.enn3developer.becom.block.TileCable;
import com.enn3developer.becom.config.BeComConfig;
import com.enn3developer.becom.packet.BeComPackets;
import com.enn3developer.becom.packet.ConfigSyncPacket;
import com.enn3developer.becom.quest.BeComQuests;
import com.enn3developer.becom.selftest.SelfTest;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** World, player and tick events BeCom follows on the server. */
public final class ServerEvents {

    /** A cable a player right-clicked, and the face they clicked: where a block they place against it goes. */
    private record Aim(EntityPlayer player, World world, int x, int y, int z, ForgeDirection face) {}

    // The latest right-click on a cable; the block it places, if any, follows in the same tick.
    private Aim aimed;

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        BeComServer becom = BeComServer.get();
        if (becom == null) return;
        long start = System.nanoTime();
        becom.tick();
        SelfTest.becomTicked(System.nanoTime() - start);
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
        if (becom == null || event.world.isRemote) return;
        becom.network.worldUnloaded(event.world);
        becom.heights.worldUnloaded(event.world);
    }

    @SubscribeEvent
    public void onWorldSave(WorldEvent.Save event) {
        BeComServer becom = BeComServer.get();
        if (becom != null) becom.heights.worldSaved(event.world);
    }

    /** The height cache follows chunks as they load and as they save; buses stop at unloaded chunks. */
    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        BeComServer becom = BeComServer.get();
        if (becom == null) return;
        becom.heights.chunkChanged(event.world, event.getChunk());
        becom.network.chunkChanged(event.world, event.getChunk().xPosition, event.getChunk().zPosition);
    }

    @SubscribeEvent
    public void onChunkUnload(ChunkEvent.Unload event) {
        BeComServer becom = BeComServer.get();
        if (becom != null)
            becom.network.chunkChanged(event.world, event.getChunk().xPosition, event.getChunk().zPosition);
    }

    @SubscribeEvent
    public void onChunkSave(ChunkDataEvent.Save event) {
        BeComServer becom = BeComServer.get();
        if (becom != null) becom.heights.chunkChanged(event.world, event.getChunk());
    }

    @SubscribeEvent
    public void onInteract(PlayerInteractEvent event) {
        if (event.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK || event.world.isRemote) return;
        aimed = event.world.getBlock(event.x, event.y, event.z) instanceof BlockPhoneCable ? new Aim(
            event.entityPlayer,
            event.world,
            event.x,
            event.y,
            event.z,
            ForgeDirection.getOrientation(event.face)) : null;
    }

    /**
     * A block placed against a cable, the way GT's machines join its pipes: unless the player sneaks, the cable opens
     * toward it, if it takes the cable. After every other mod has had its say, so a placement one cancels joins
     * nothing.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPlace(BlockEvent.PlaceEvent event) {
        Aim aim = aimed;
        aimed = null;
        if (aim == null || aim.player() != event.player || aim.world() != event.world || event.player.isSneaking())
            return;
        ForgeDirection face = aim.face();
        if (aim.x() + face.offsetX != event.x || aim.y() + face.offsetY != event.y || aim.z() + face.offsetZ != event.z)
            return;
        if (event.world.getTileEntity(aim.x(), aim.y(), aim.z()) instanceof TileCable cable) cable.connect(face);
    }

    /** Rockets are Galacticraft's entities, so BeCom follows them as they join their world (9.3). */
    @SubscribeEvent
    public void onEntityJoin(EntityJoinWorldEvent event) {
        BeComServer becom = BeComServer.get();
        if (becom != null && !event.world.isRemote) becom.rockets.joined(event.entity);
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        BeComServer becom = BeComServer.get();
        if (becom == null || !(event.player instanceof EntityPlayerMP player)) return;
        // The server's rules for the client's tooltips and holograms.
        BeComPackets.send(new ConfigSyncPacket(BeComConfig.get()), player);
        becom.network.sendWires(player);
    }

    @SubscribeEvent
    public void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        BeComServer becom = BeComServer.get();
        if (becom != null && event.player instanceof EntityPlayerMP player) becom.network.sendWires(player);
    }

    /** Dying in the Nether or leaving the End respawns into another world without a dimension-change event. */
    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        BeComServer becom = BeComServer.get();
        if (becom != null && event.player instanceof EntityPlayerMP player) becom.network.sendWires(player);
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        BeComServer becom = BeComServer.get();
        if (becom == null) return;
        becom.calls.logout(event.player.getUniqueID());
        becom.radio.logout(event.player.getUniqueID());
        becom.carried.logout(event.player.getUniqueID());
        becom.links.forget(event.player.getUniqueID());
    }
}
