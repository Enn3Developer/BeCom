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

import com.enn3developer.becom.logic.net.NetNode;
import com.enn3developer.becom.logic.net.NetworkModel;
import com.enn3developer.becom.logic.net.NodeKind;

/**
 * One dimension's network (DESIGN.md 14): every node with its position, owner and links, and which links a clearance
 * check found blocked. Saved in the dimension's own data folder.
 */
public final class NetworkSavedData extends WorldSavedData {

    public static final String NAME = "becom_network";
    public static final int FORMAT = 1;

    private final NetworkModel model = new NetworkModel();
    private long savedVersion;
    private NBTTagCompound tooNewData;
    // What this BeCom found it doesn't understand, which means a newer BeCom saved the file.
    private String notUnderstood;
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
        if (data.notUnderstood != null) throw SavedDataFormat.notUnderstood(what, data.notUnderstood);
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
        // Kinds are added without a format bump (NodeKind), so a kind this BeCom doesn't know means a newer one saved
        // the file: keep it all, untouched, rather than drop those nodes and save over it.
        for (int i = 0; i < nodes.tagCount(); i++) {
            String name = nodes.getCompoundTagAt(i)
                .getString("kind");
            if (kindOf(name) == null) {
                notUnderstood(nbt, "a node kind it doesn't know, \"" + name + "\"");
                return;
            }
        }
        for (int i = 0; i < nodes.tagCount(); i++) {
            NBTTagCompound tag = nodes.getCompoundTagAt(i);
            NodeKind kind = kindOf(tag.getString("kind"));
            UUID owner = tag.hasKey("ownerMost") ? new UUID(tag.getLong("ownerMost"), tag.getLong("ownerLeast")) : null;
            NetNode node = new NetNode(tag.getLong("pos"), kind, owner);
            node.setHeight(tag.getInteger("height"));
            node.setFormed(tag.getBoolean("formed"));
            model.add(node);
        }
        // Links go in once every node exists, so a link to a node saved later in the list isn't lost.
        for (int i = 0; i < nodes.tagCount(); i++) {
            NBTTagCompound tag = nodes.getCompoundTagAt(i);
            long pos = tag.getLong("pos");
            if (model.node(pos) == null) continue;
            NBTTagList links = tag.getTagList("links", Constants.NBT.TAG_COMPOUND);
            for (int j = 0; j < links.tagCount(); j++) {
                long other = links.getCompoundTagAt(j)
                    .getLong("pos");
                NetNode a = model.node(pos);
                NetNode b = model.node(other);
                if (b == null || model.linked(pos, other)) continue;
                // More links than a kind has slots: a newer BeCom with more slots wrote this.
                if (!a.hasFreeSlot() || !b.hasFreeSlot()) {
                    notUnderstood(nbt, "a " + a.kind() + " with more links than this BeCom gives it");
                    return;
                }
                model.link(pos, other);
            }
        }
        NBTTagList blocked = nbt.getTagList("blocked", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < blocked.tagCount(); i++) {
            NBTTagCompound tag = blocked.getCompoundTagAt(i);
            long a = tag.getLong("a");
            long b = tag.getLong("b");
            if (model.linked(a, b)) model.setBlocked(a, b, true);
        }
        savedVersion = model.version();
        // Last: anything thrown above leaves the data unread, and so never saved over the file.
        read = true;
    }

    private void notUnderstood(NBTTagCompound nbt, String detail) {
        notUnderstood = detail;
        tooNewData = (NBTTagCompound) nbt.copy();
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
            tag.setInteger("height", node.height());
            tag.setBoolean("formed", node.formed());
            NBTTagList links = new NBTTagList();
            for (long other : node.links()) {
                NBTTagCompound link = new NBTTagCompound();
                link.setLong("pos", other);
                links.appendTag(link);
            }
            tag.setTag("links", links);
            nodes.appendTag(tag);
        }
        nbt.setTag("nodes", nodes);
        NBTTagList blocked = new NBTTagList();
        for (NetworkModel.LinkKey key : model.blockedLinks()) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setLong("a", key.a());
            tag.setLong("b", key.b());
            blocked.appendTag(tag);
        }
        nbt.setTag("blocked", blocked);
        savedVersion = model.version();
    }
}
