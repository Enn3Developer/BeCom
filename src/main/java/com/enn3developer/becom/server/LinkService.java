package com.enn3developer.becom.server;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

import com.enn3developer.becom.logic.Tunables;
import com.enn3developer.becom.logic.net.BlockPos;
import com.enn3developer.becom.logic.net.Clearance;
import com.enn3developer.becom.logic.net.LinkRules;
import com.enn3developer.becom.logic.net.NetNode;
import com.enn3developer.becom.logic.net.NetworkModel;
import com.enn3developer.becom.logic.net.NodeKind;
import com.enn3developer.becom.logic.net.NodeRef;
import com.enn3developer.becom.logic.space.Portals;
import com.enn3developer.becom.quest.BeComQuests;
import com.enn3developer.becom.quest.QuestTask;

/**
 * Linking nodes with a GT wrench (DESIGN.md 5.1, 12.2): the first node is remembered for the player for a while, and
 * the second one links to it, or unlinks it when the two are already linked. Anyone may link (D37); only an owner of
 * either end, or their team, may unlink. Two Portal Relays in different dimensions pair the same way, through their
 * portal (9.4).
 */
public final class LinkService {

    private final NetworkService network;
    private final long halfLinkTicks;
    private final double relayReach;
    private final Map<UUID, Pending> pending = new HashMap<>();

    public LinkService(NetworkService network, Tunables tunables) {
        this.network = network;
        this.halfLinkTicks = (long) tunables.halfLinkSeconds() * 20;
        this.relayReach = tunables.relayPortalDistance();
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
        if (first != null && first.dimension() != dimension && node.kind() == NodeKind.PORTAL_RELAY) {
            NetworkModel firstModel = modelOf(first.dimension());
            NetNode other = firstModel == null ? null : firstModel.node(first.pos());
            if (other != null && other.kind() == NodeKind.PORTAL_RELAY) {
                pending.remove(id);
                return pair(
                    player,
                    new NodeRef(first.dimension(), first.pos()),
                    other,
                    new NodeRef(dimension, pos),
                    node);
            }
        }
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

    /**
     * The second relay of a pair is wrenched, on the far side of the portal (DESIGN.md 9.4): they pair, or unpair when
     * paired already. A pair joins the Overworld to a dimension a portal leads to (9.5), and each relay has one
     * partner.
     */
    private Outcome pair(EntityPlayer player, NodeRef firstRef, NetNode first, NodeRef secondRef, NetNode second) {
        if (secondRef.equals(network.partnerOf(firstRef))) {
            if (!Ownership.mayManage(player, first.owner()) && !Ownership.mayManage(player, second.owner())) {
                tell(player, "becom.link.not_yours");
                return Outcome.NOT_ALLOWED;
            }
            network.unpair(firstRef, secondRef);
            tell(player, "becom.link.unpaired");
            return Outcome.UNLINKED;
        }
        Portals.Kind portal = Portals.pairKind(
            firstRef.dimension(),
            NetworkService.providerOf(firstRef.dimension()),
            secondRef.dimension(),
            NetworkService.providerOf(secondRef.dimension()));
        if (portal == null) {
            tell(player, "becom.link.refused.relay_dimensions");
            return Outcome.REFUSED;
        }
        if (!Portals.sameLink(
            portal,
            NetworkService.movementFactorOf(firstRef.dimension()),
            BlockPos.x(firstRef.pos()),
            BlockPos.z(firstRef.pos()),
            NetworkService.movementFactorOf(secondRef.dimension()),
            BlockPos.x(secondRef.pos()),
            BlockPos.z(secondRef.pos()),
            relayReach)) {
            tell(player, "becom.link.refused.relay_portal");
            return Outcome.REFUSED;
        }
        if (taken(firstRef, first) || taken(secondRef, second)) {
            tell(player, "becom.link.refused.relay_paired");
            return Outcome.REFUSED;
        }
        network.pair(firstRef, secondRef);
        tell(player, "becom.link.paired");
        BeComQuests.complete(QuestTask.PORTAL_RELAY_PAIR, List.of(player.getUniqueID()));
        return Outcome.LINKED;
    }

    /**
     * Whether a relay is paired with another: one paired with it in turn, or one in a dimension that isn't loaded,
     * which can't be told and so is kept. Unlinking someone's pair is for its owners (D37).
     */
    private boolean taken(NodeRef ref, NetNode relay) {
        NodeRef partner = relay.partner();
        if (partner == null) return false;
        if (network.model(partner.dimension()) == null) return true;
        return network.partnerOf(ref) != null;
    }

    /**
     * A dimension's network, loading its world if it unloaded since the player left it: going through a portal takes
     * a while, and the far side's half of a pair is in that dimension's saved data.
     */
    private NetworkModel modelOf(int dimension) {
        WorldServer world = DimensionManager.getWorld(dimension);
        if (world == null && DimensionManager.isDimensionRegistered(dimension)) {
            DimensionManager.initDimension(dimension);
            world = DimensionManager.getWorld(dimension);
        }
        return world == null ? null : network.model(world);
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
