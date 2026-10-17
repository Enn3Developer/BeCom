package com.enn3developer.becom.server;

import net.minecraft.entity.player.EntityPlayer;
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
            if (machine.getMachine() instanceof IWirelessAccessPoint wap && wap.isActive() && reaches(wap, player)) {
                return grid;
            }
        }
        return null;
    }

    /** AE2's test: same world, and closer to the access point than its range, boosters counted. */
    private static boolean reaches(IWirelessAccessPoint wap, EntityPlayer player) {
        DimensionalCoord at = wap.getLocation();
        if (at.getWorld() != player.worldObj) return false;
        double range = wap.getRange();
        double dx = at.x - player.posX;
        double dy = at.y - player.posY;
        double dz = at.z - player.posZ;
        return dx * dx + dy * dy + dz * dz < range * range;
    }
}
