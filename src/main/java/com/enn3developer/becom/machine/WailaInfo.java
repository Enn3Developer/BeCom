package com.enn3developer.becom.machine;

import java.util.function.Supplier;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
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

    /** The reach the server gives a player it doesn't keep one for, as Forge does. */
    private static final double DEFAULT_REACH = 5;

    /**
     * How much further than their reach a player's eyes may be from a machine's middle and still get its lines, in
     * blocks. WAILA measures the reach to the face it hits, up to 0.87 nearer than the middle, and the rest covers a
     * step taken while the request travels. WAILA answers for any position a client names, so without a limit a
     * modified client could read every Telephone's number and owner from anywhere.
     */
    static final double SLACK = 3;

    private static boolean warned;

    private final Supplier<InfoLines> source;

    public WailaInfo(Supplier<InfoLines> source) {
        this.source = source;
    }

    /** Writes the machine's lines for a player looking at it from close by. A player anywhere else gets nothing. */
    public void write(NBTTagCompound tag, EntityPlayer player, World world, int x, int y, int z) {
        if (player == null || world == null) return;
        // The reach the server keeps for the player: 5 blocks, and 3.5 more for each of Botania's Rings of Far Reach.
        double reach = player instanceof EntityPlayerMP mp ? mp.theItemInWorldManager.getBlockReachDistance()
            : DEFAULT_REACH;
        int dimension = world.provider.dimensionId;
        if (!near(reach, player.dimension, player.posX, player.posY, player.posZ, dimension, x, y, z)) return;
        write(tag);
    }

    /**
     * Whether a player with {@code reach}, whose feet stand at {@code px, py, pz}, is close enough to a machine to be
     * looking at it.
     */
    static boolean near(double reach, int playerDimension, double px, double py, double pz, int dimension, int x, int y,
        int z) {
        if (playerDimension != dimension) return false;
        double dx = px - (x + 0.5), dy = py + EYES - (y + 0.5), dz = pz - (z + 0.5);
        double limit = reach + SLACK;
        return dx * dx + dy * dy + dz * dz <= limit * limit;
    }

    /** Writes the machine's lines. */
    void write(NBTTagCompound tag) {
        InfoLines lines;
        try {
            lines = source.get();
        } catch (RuntimeException e) {
            // A machine broken since it was looked at, say. WAILA would catch it, but then send nothing, GT's lines
            // included; and on GT's drone camera, which asks the same way, nothing catches it short of the connection.
            if (!warned) BeCom.LOG.warn("BeCom couldn't work out a machine's WAILA lines", e);
            warned = true;
            return;
        }
        lines.write(tag);
    }
}
