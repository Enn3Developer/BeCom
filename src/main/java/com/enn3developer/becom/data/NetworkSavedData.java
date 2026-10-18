package com.enn3developer.becom.data;

import java.io.File;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;
import net.minecraftforge.common.util.Constants;

import com.enn3developer.becom.BeCom;
import com.enn3developer.becom.logic.net.BlockPos;
import com.enn3developer.becom.logic.net.NetNode;
import com.enn3developer.becom.logic.net.NetworkModel;
import com.enn3developer.becom.logic.net.NodeKind;
import com.enn3developer.becom.logic.net.NodeRef;

/**
 * One dimension's network: every node with its position, its owner and, for a Portal Relay, its partner. Saved in the
 * dimension's own data folder.
 */
public final class NetworkSavedData extends WorldSavedData {

    public static final String NAME = "becom_network";
    public static final int FORMAT = 1;

    private final NetworkModel model = new NetworkModel();
    private long savedVersion;
    private NBTTagCompound tooNewData;
    private boolean read;

    public NetworkSavedData(String name) {
        super(name);
    }

    /**
     * The network of {@code world}'s dimension, loading it on first use.
     *
     * @throws IllegalStateException when the saved data is newer than this BeCom understands
     */
    public static NetworkSavedData get(World world) {
        return load(world.perWorldStorage, file(world), "the network of dimension " + world.provider.dimensionId);
    }

    /** Loads the data from {@code storage}, whose file is {@code file}; {@link #get} for a world. */
    static NetworkSavedData load(MapStorage storage, File file, String what) {
        NetworkSavedData data = (NetworkSavedData) storage.loadData(NetworkSavedData.class, NAME);
        if (data == null) {
            data = new NetworkSavedData(NAME);
            // Nothing on disk to read: a new world. Counts as read, so a later get() after the first save is fine.
            data.read = true;
            storage.setData(NAME, data);
        } else if (SavedDataFormat.unreadable(file != null && file.isFile(), data.read)) {
            data.tooNewData = new NBTTagCompound();
            throw SavedDataFormat.couldNotRead(what, file);
        }
        if (data.tooNewData != null) {
            throw SavedDataFormat.newerThanUnderstood(what, SavedDataFormat.versionOf(data.tooNewData), FORMAT);
        }
        return data;
    }

    /** Where {@code world.perWorldStorage} keeps this file: the dimension's own data folder. */
    private static File file(World world) {
        if (!(world instanceof WorldServer server)) return null;
        File dir = server.getChunkSaveLocation();
        return dir == null ? null : new File(new File(dir, "data"), NAME + ".dat");
    }

    /** Whether {@link #readFromNBT} ran; false for data vanilla failed to read. */
    public boolean wasRead() {
        return read;
    }

    public NetworkModel model() {
        return model;
    }

    /** Whether loading found data from a newer BeCom, which is then never written back. */
    public boolean isTooNew() {
        return tooNewData != null;
    }

    @Override
    public boolean isDirty() {
        if (tooNewData != null) return false;
        return super.isDirty() || model.version() != savedVersion;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        if (SavedDataFormat.tooNew(nbt, FORMAT)) {
            tooNewData = (NBTTagCompound) nbt.copy();
            read = true;
            return;
        }
        // Format 1 is the first; later formats upgrade from here.
        NBTTagList nodes = nbt.getTagList("nodes", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < nodes.tagCount(); i++) {
            NBTTagCompound tag = nodes.getCompoundTagAt(i);
            NodeKind kind = kindOf(tag.getString("kind"));
            // A kind this BeCom doesn't have, from an older one or a newer one, is dropped: its machine, if it's still
            // there, joins again when its chunk loads.
            if (kind == null) {
                BeCom.LOG.warn(
                    "Dropping a network node of a kind this BeCom doesn't have, \"{}\", at {}",
                    tag.getString("kind"),
                    BlockPos.toString(tag.getLong("pos")));
                continue;
            }
            UUID owner = tag.hasKey("ownerMost") ? new UUID(tag.getLong("ownerMost"), tag.getLong("ownerLeast")) : null;
            NetNode node = new NetNode(tag.getLong("pos"), kind, owner);
            if (tag.hasKey("partnerPos")) {
                node.setPartner(new NodeRef(tag.getInteger("partnerDim"), tag.getLong("partnerPos")));
            }
            model.add(node);
        }
        savedVersion = model.version();
        // Last: anything thrown above leaves the data unread, and so never saved over the file.
        read = true;
    }

    private static NodeKind kindOf(String name) {
        try {
            return NodeKind.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        if (tooNewData != null) {
            // Never reached while isDirty() is false, but if anything saves it anyway, write back what was read.
            for (String key : tooNewData.func_150296_c()) nbt.setTag(key, tooNewData.getTag(key));
            return;
        }
        SavedDataFormat.write(nbt, FORMAT);
        NBTTagList nodes = new NBTTagList();
        for (NetNode node : model.nodes()) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setLong("pos", node.pos());
            tag.setString(
                "kind",
                node.kind()
                    .name());
            if (node.owner() != null) {
                tag.setLong(
                    "ownerMost",
                    node.owner()
                        .getMostSignificantBits());
                tag.setLong(
                    "ownerLeast",
                    node.owner()
                        .getLeastSignificantBits());
            }
            // A Portal Relay's partner: only relays have one, and a BeCom from before relays refuses their kind, so
            // the keys need no new format.
            if (node.partner() != null) {
                tag.setInteger(
                    "partnerDim",
                    node.partner()
                        .dimension());
                tag.setLong(
                    "partnerPos",
                    node.partner()
                        .pos());
            }
            nodes.appendTag(tag);
        }
        nbt.setTag("nodes", nodes);
        savedVersion = model.version();
    }
}
