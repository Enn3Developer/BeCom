package com.enn3developer.becom.server;

import java.util.Random;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

import com.enn3developer.becom.block.TileTelephone;
import com.enn3developer.becom.data.GlobalSavedData;
import com.enn3developer.becom.logic.net.BlockPos;
import com.enn3developer.becom.logic.phone.PhoneKind;
import com.enn3developer.becom.logic.phone.PhoneRecord;
import com.enn3developer.becom.logic.phone.PhoneRegistry;

/** The world's phone registry (DESIGN.md 8.2, 14) and where block phones stand. Server thread only. */
public final class PhoneService {

    private final Random random = new Random();
    private PhoneRegistry registry = new PhoneRegistry();

    /** Uses the registry kept in the Overworld's data; called once the server's worlds have loaded. */
    public void attach(GlobalSavedData data) {
        registry = data.phones();
    }

    /** The world's registry: the Overworld's once attached, an empty one before. */
    public PhoneRegistry registry() {
        return registry;
    }

    /** A new number for a Telephone placed at {@code pos}; its owner is whoever placed it (DESIGN.md 12.1). */
    public PhoneRecord registerTelephone(World world, long pos, EntityPlayer placer) {
        PhoneRecord record = registry().create(
            PhoneKind.TELEPHONE,
            placer == null ? null : placer.getUniqueID(),
            placer == null ? "" : placer.getCommandSenderName(),
            random);
        record.setLocation(world.provider.dimensionId, pos);
        registry().changed();
        return record;
    }

    public PhoneRecord record(int number) {
        return registry().get(number);
    }

    public void unregister(int number) {
        registry().remove(number);
    }

    /** The loaded Telephone with {@code number}, or null when it isn't a Telephone or its chunk isn't loaded. */
    public TileTelephone telephone(int number) {
        PhoneRecord record = registry().get(number);
        if (record == null || record.kind() != PhoneKind.TELEPHONE) return null;
        WorldServer world = DimensionManager.getWorld(record.dimension());
        if (world == null) return null;
        long pos = record.pos();
        int x = BlockPos.x(pos), y = BlockPos.y(pos), z = BlockPos.z(pos);
        if (!world.getChunkProvider()
            .chunkExists(x >> 4, z >> 4)) return null;
        TileEntity tile = world.getTileEntity(x, y, z);
        return tile instanceof TileTelephone phone && phone.number() == number ? phone : null;
    }

    /**
     * Makes {@code tile} the home of its number again if the Telephone moved (a frame or a dolly carries its saved
     * number along without breaking it). A copy whose original still stands with the number is left alone here; it
     * gets a number of its own on its first tick (X17). Returns whether the record now points at {@code tile}.
     */
    public boolean relocate(TileTelephone tile) {
        int number = tile.number();
        PhoneRecord record = number < 0 ? null : registry().get(number);
        if (record == null || record.kind() != PhoneKind.TELEPHONE) return false;
        World world = tile.getWorldObj();
        long pos = BlockPos.pack(tile.xCoord, tile.yCoord, tile.zCoord);
        if (record.dimension() == world.provider.dimensionId && record.pos() == pos) return true;
        // The original still stands: this is a copy. One saved by a newer BeCom counts too, though it shows no number.
        if (standsAt(record)) return false;
        // Its old spot isn't loaded, so there's no telling; a move happens with both spots loaded, so this is most
        // likely a copy, and a copy gets its own number on its first tick.
        if (!isLoaded(record)) return false;
        record.setLocation(world.provider.dimensionId, pos);
        registry().changed();
        return true;
    }

    /** Whether a Telephone holding {@code record}'s number, or one this BeCom leaves alone, stands where it says. */
    private static boolean standsAt(PhoneRecord record) {
        WorldServer world = DimensionManager.getWorld(record.dimension());
        long pos = record.pos();
        int x = BlockPos.x(pos), z = BlockPos.z(pos);
        if (world == null || !world.getChunkProvider()
            .chunkExists(x >> 4, z >> 4)) return false;
        TileEntity tile = world.getTileEntity(x, BlockPos.y(pos), z);
        return tile instanceof TileTelephone phone && (phone.isTooNew() || phone.number() == record.number());
    }

    /**
     * A fresh number for a copy of a Telephone, owned by the original's owner and with its settings: a copy made with a
     * world editor, or a phone carried off from a spot that has since unloaded, stays its owner's rather than going to
     * whoever uses it first. Its history stays with the original.
     */
    public PhoneRecord registerCopy(World world, long pos, PhoneRecord original) {
        PhoneRecord record = registry().create(PhoneKind.TELEPHONE, original.owner(), original.ownerName(), random);
        record.setLocation(world.provider.dimensionId, pos);
        // The owner's settings come along, so a renumbered phone doesn't turn up in the contacts of players it blocks.
        record.setDoNotDisturb(original.doNotDisturb());
        record.setHidden(original.hidden());
        for (java.util.Map.Entry<java.util.UUID, String> blocked : original.blocked()
            .entrySet()) record.restoreBlock(blocked.getKey(), blocked.getValue());
        registry().changed();
        return record;
    }

    private static boolean isLoaded(PhoneRecord record) {
        WorldServer world = DimensionManager.getWorld(record.dimension());
        long pos = record.pos();
        return world != null && world.getChunkProvider()
            .chunkExists(BlockPos.x(pos) >> 4, BlockPos.z(pos) >> 4);
    }

    /** The name the contacts list and history show for a phone: its owner's. */
    public String name(int number) {
        PhoneRecord record = registry().get(number);
        return record == null ? "" : record.ownerName();
    }
}
