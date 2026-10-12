package com.enn3developer.becom.server;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

import com.enn3developer.becom.logic.Tunables;
import com.enn3developer.becom.logic.net.BlockPos;
import com.enn3developer.becom.logic.net.Clearance;
import com.enn3developer.becom.logic.net.LinkRules;
import com.enn3developer.becom.logic.net.NetNode;
import com.enn3developer.becom.logic.net.NetworkModel;

/**
 * Linking nodes with a GT wrench (DESIGN.md 5.1, 12.2): the first node is remembered for the player for a while, and
 * the second one links to it, or unlinks it when the two are already linked. Anyone may link (D37); only an owner of
 * either end, or their team, may unlink.
 */
public final class LinkService {

    private final NetworkService network;
    private final long halfLinkTicks;
    private final Map<UUID, Pending> pending = new HashMap<>();

    public LinkService(NetworkService network, Tunables tunables) {
        this.network = network;
        this.halfLinkTicks = (long) tunables.halfLinkSeconds() * 20;
    }

    private record Pending(int dimension, long pos, long tick) {}

    public enum Outcome {
        SELECTED,
        CANCELLED,
        LINKED,
        UNLINKED,
        NOT_ALLOWED,
        BLOCKED,
        REFUSED,
        NO_NODE
    }

    /** {@code player} wrenched the node at {@code pos}. */
    public Outcome wrench(EntityPlayer player, World world, long pos) {
        expire();
        NetworkModel model = network.model(world);
        NetNode node = model.node(pos);
        if (node == null) {
            tell(player, "becom.link.no_node");
            return Outcome.NO_NODE;
        }
        UUID id = player.getUniqueID();
        int dimension = world.provider.dimensionId;
        Pending first = pending.get(id);
        if (first == null || first.dimension() != dimension || model.node(first.pos()) == null) {
            pending.put(id, new Pending(dimension, pos, network.currentTick()));
            tell(player, "becom.link.selected", BlockPos.toString(pos));
            return Outcome.SELECTED;
        }
        pending.remove(id);
        if (first.pos() == pos) {
            tell(player, "becom.link.cancelled");
            return Outcome.CANCELLED;
        }
        NetNode other = model.node(first.pos());
        if (model.linked(first.pos(), pos)) {
            if (!Ownership.mayManage(player, node.owner()) && !Ownership.mayManage(player, other.owner())) {
                tell(player, "becom.link.not_yours");
                return Outcome.NOT_ALLOWED;
            }
            model.unlink(first.pos(), pos);
            tell(player, "becom.link.unlinked");
            return Outcome.UNLINKED;
        }
        LinkRules.Result result = network.rules()
            .check(model, other, node);
        if (result != LinkRules.Result.OK) {
            tell(
                player,
                "becom.link.refused." + result.name()
                    .toLowerCase(Locale.ROOT),
                (int) Math.ceil(other.distanceTo(node)),
                (int) Math.floor(
                    network.rules()
                        .maxSpan(other, node)));
            return Outcome.REFUSED;
        }
        if (other.kind()
            .pylonHead()
            && node.kind()
                .pylonHead()
            && network.clearance(world, other.pos(), pos) == Clearance.Result.BLOCKED) {
            tell(player, "becom.link.blocked");
            return Outcome.BLOCKED;
        }
        model.link(other.pos(), pos);
        tell(player, "becom.link.linked", (int) Math.ceil(other.distanceTo(node)));
        return Outcome.LINKED;
    }

    /** Forgets half-made links older than the configured time, so the map doesn't only grow. */
    public void expire() {
        long now = network.currentTick();
        Iterator<Pending> it = pending.values()
            .iterator();
        while (it.hasNext()) {
            if (now - it.next()
                .tick() > halfLinkTicks) it.remove();
        }
    }

    public void forget(UUID player) {
        pending.remove(player);
    }

    private static void tell(EntityPlayer player, String key, Object... args) {
        player.addChatComponentMessage(new ChatComponentTranslation(key, args));
    }
}
