package com.enn3developer.becom.data;

import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraftforge.common.util.Constants;

import com.enn3developer.becom.BeCom;
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

    public NetworkSavedData(String name) {
        super(name);
    }

    /**
     * The network of {@code world}'s dimension, loading it on first use.
     *
     * @throws IllegalStateException when the saved data is newer than this BeCom understands
     */
    public static NetworkSavedData get(World world) {
        NetworkSavedData data = (NetworkSavedData) world.perWorldStorage.loadData(NetworkSavedData.class, NAME);
        if (data == null) {
            data = new NetworkSavedData(NAME);
            world.perWorldStorage.setData(NAME, data);
        }
        if (data.tooNewData != null) {
            throw SavedDataFormat.newerThanUnderstood(
                "the network of dimension " + world.provider.dimensionId,
                SavedDataFormat.versionOf(data.tooNewData),
                FORMAT);
        }
        return data;
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
            return;
        }
        // Format 1 is the first; later formats upgrade from here.
        NBTTagList nodes = nbt.getTagList("nodes", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < nodes.tagCount(); i++) {
            NBTTagCompound tag = nodes.getCompoundTagAt(i);
            NodeKind kind;
            try {
                kind = NodeKind.valueOf(tag.getString("kind"));
            } catch (IllegalArgumentException e) {
                BeCom.LOG.warn("Dropping a saved network node of unknown kind {}", tag.getString("kind"));
                continue;
            }
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
                // Never more links than slots, whatever the file says.
                if (b != null && (model.linked(pos, other) || a.hasFreeSlot() && b.hasFreeSlot())) {
                    model.link(pos, other);
                }
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
