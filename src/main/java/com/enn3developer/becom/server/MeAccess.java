package com.enn3developer.becom.server;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.AEApi;
import appeng.api.features.ILocatable;
import appeng.api.implementations.tiles.IWirelessAccessPoint;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.util.DimensionalCoord;
import appeng.tile.networking.TileWireless;

/**
 * Where an ME Phone has service: inside the wireless range of the ME network it's linked to,
 * by AE2's own rule, so GTNH's ranges and booster cards apply unchanged. AE2 keeps that rule for its own wireless
 * terminals, and an ME Phone isn't one, so this is the same test on the same access points.
 */
public final class MeAccess {

    private MeAccess() {}

    /** The ME network {@code key} links to, if {@code player} stands in its wireless range; else null. */
    public static IGrid gridInRange(String key, EntityPlayer player) {
        return gridInRange(key, player.worldObj, player.posX, player.posY, player.posZ);
    }

    /** The ME network {@code key} links to, if a spot in {@code world} is in its wireless range; else null. */
    public static IGrid gridInRange(String key, World world, double x, double y, double z) {
        if (key == null || key.isEmpty()) return null;
        long serial;
        try {
            serial = Long.parseLong(key);
        } catch (NumberFormatException e) {
            return null;
        }
        ILocatable found = AEApi.instance()
            .registries()
            .locatable()
            .getLocatableBy(serial);
        if (!(found instanceof IGridHost host)) return null;
        IGridNode node = host.getGridNode(ForgeDirection.UNKNOWN);
        if (node == null) return null;
        IGrid grid = node.getGrid();
        if (grid == null) return null;
        for (IGridNode machine : grid.getMachines(TileWireless.class)) {
            if (machine.getMachine() instanceof IWirelessAccessPoint wap && wap.isActive()
                && reaches(wap, world, x, y, z)) {
                return grid;
            }
        }
        return null;
    }

    /** AE2's test: same world, and closer to the access point than its range, boosters counted. */
    private static boolean reaches(IWirelessAccessPoint wap, World world, double x, double y, double z) {
        DimensionalCoord at = wap.getLocation();
        if (at.getWorld() != world) return false;
        double range = wap.getRange();
        double dx = at.x - x;
        double dy = at.y - y;
        double dz = at.z - z;
        return dx * dx + dy * dy + dz * dz < range * range;
    }
}
