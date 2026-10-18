package com.enn3developer.becom.data;

import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
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

    private final NetworkModel model = new NetworkModel();
    private long savedVersion;

    public NetworkSavedData(String name) {
        super(name);
    }

    /** The network of {@code world}'s dimension, loading it on first use. */
    public static NetworkSavedData get(World world) {
        MapStorage storage = world.perWorldStorage;
        NetworkSavedData data = (NetworkSavedData) storage.loadData(NetworkSavedData.class, NAME);
        if (data == null) {
            data = new NetworkSavedData(NAME);
            storage.setData(NAME, data);
        }
        return data;
    }

    public NetworkModel model() {
        return model;
    }

    @Override
    public boolean isDirty() {
        return super.isDirty() || model.version() != savedVersion;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
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
