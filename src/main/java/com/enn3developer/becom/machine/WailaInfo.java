package com.enn3developer.becom.machine;

import java.util.function.Supplier;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import com.enn3developer.becom.BeCom;

/**
 * A machine's lines for WAILA, worked out as WAILA asks. Its request is a mod packet, which 1.7.10 queues for the
 * server thread like any packet without priority, so the machine's state can be read as it stands.
 */
public final class WailaInfo {

    /** How far a player's eyes stand above their feet, which the server keeps as their position. */
    private static final double EYES = 1.62;

    /**
     * How near a player's eyes must be to a machine's middle to get its lines, in blocks. WAILA shows what's under the
     * crosshair: up to 5 blocks off in creative, and 3.5 more with Botania's Ring of Far Reach; the rest covers a step
     * taken while the request travels. WAILA answers for any position a client names, so without this a modified client
     * could read every Telephone's number and owner from anywhere.
     */
    static final double REACH = 12;

    private static boolean warned;

    private final Supplier<InfoLines> source;

    public WailaInfo(Supplier<InfoLines> source) {
        this.source = source;
    }

    /** Writes the machine's lines for a player looking at it from close by. A player anywhere else gets nothing. */
    public void write(NBTTagCompound tag, EntityPlayer player, World world, int x, int y, int z) {
        if (player == null || world == null) return;
        if (!near(player.dimension, player.posX, player.posY, player.posZ, world.provider.dimensionId, x, y, z)) return;
        write(tag);
    }

    /** Whether a player whose feet stand at {@code px, py, pz} is close enough to a machine to be looking at it. */
    static boolean near(int playerDimension, double px, double py, double pz, int dimension, int x, int y, int z) {
        if (playerDimension != dimension) return false;
        double dx = px - (x + 0.5), dy = py + EYES - (y + 0.5), dz = pz - (z + 0.5);
        return dx * dx + dy * dy + dz * dz <= REACH * REACH;
    }

    /** Writes the machine's lines. */
    void write(NBTTagCompound tag) {
        InfoLines lines;
        try {
            lines = source.get();
        } catch (RuntimeException e) {
            // A machine broken since it was looked at, say. WAILA is a nicety: an exception here would cost the player
            // their connection, or a single-player world.
            if (!warned) BeCom.LOG.warn("BeCom couldn't work out a machine's WAILA lines", e);
            warned = true;
            return;
        }
        lines.write(tag);
    }
}
