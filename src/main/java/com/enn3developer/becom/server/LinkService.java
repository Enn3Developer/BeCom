package com.enn3developer.becom.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

import com.enn3developer.becom.block.BlockPhoneCable;
import com.enn3developer.becom.logic.Tunables;
import com.enn3developer.becom.logic.net.BlockPos;
import com.enn3developer.becom.logic.net.CableTier;
import com.enn3developer.becom.logic.net.Clearance;
import com.enn3developer.becom.logic.net.LinkRules;
import com.enn3developer.becom.logic.net.NetNode;
import com.enn3developer.becom.logic.net.NetworkModel;

/**
 * Linking nodes with a GT wrench: the first node is remembered for the player for a while, and
 * the second one links to it, stringing a span with the first cable in the player's hotbar, one item for every few
 * blocks of span. Wrenching a linked pair again with cable adds it to the span's bundle, or, when the bundle is full,
 * swaps the poorest cable on it for the better one; with no cable in the hotbar it unlinks them, and the cables come
 * back. Anyone may link; only an owner of either end, or their team, may change or unlink a span.
 */
public final class LinkService {

    private final NetworkService network;
    private final long halfLinkTicks;
    private final int bundleSize;
    private final int blocksPerItem;
    private final Map<UUID, Pending> pending = new HashMap<>();

    public LinkService(NetworkService network, Tunables tunables) {
        this.network = network;
        this.halfLinkTicks = (long) tunables.halfLinkSeconds() * 20;
        this.bundleSize = tunables.bundleSize();
        this.blocksPerItem = tunables.cableBlocksPerItem();
    }

    private record Pending(int dimension, long pos, long tick) {}

    public enum Outcome {
        SELECTED,
        CANCELLED,
        LINKED,
        UNLINKED,
        CABLE_ADDED,
        CABLE_UPGRADED,
        BUNDLE_FULL,
        NEEDS_CABLE,
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
        CableTier cable = hotbarCable(player);
        int cost = cost(other, node);
        if (model.linked(first.pos(), pos)) {
            if (!Ownership.mayManage(player, node.owner()) && !Ownership.mayManage(player, other.owner())) {
                tell(player, "becom.link.not_yours");
                return Outcome.NOT_ALLOWED;
            }
            if (cable == null) {
                List<CableTier> cables = model.cables(first.pos(), pos);
                model.unlink(first.pos(), pos);
                for (CableTier tier : cables) give(player, tier, cost);
                tell(player, "becom.link.unlinked");
                return Outcome.UNLINKED;
            }
            return restring(player, model, first.pos(), pos, cable, cost);
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
        if (cable == null) {
            tell(player, "becom.link.needs_cable", cost);
            return Outcome.NEEDS_CABLE;
        }
        if (!take(player, cable, cost)) {
            tell(player, "becom.link.short_of_cable", cost, InfoName.of(cable));
            return Outcome.NEEDS_CABLE;
        }
        model.link(other.pos(), pos, List.of(cable));
        tell(player, "becom.link.linked", (int) Math.ceil(other.distanceTo(node)), InfoName.of(cable));
        return Outcome.LINKED;
    }

    /**
     * {@code player} wrenched a linked pair again holding {@code cable}: it joins the span's bundle, or replaces the
     * poorest cable there when the bundle is full and that one is worse.
     */
    private Outcome restring(EntityPlayer player, NetworkModel model, long a, long b, CableTier cable, int cost) {
        List<CableTier> cables = new ArrayList<>(model.cables(a, b));
        if (cables.size() < bundleSize) {
            if (!take(player, cable, cost)) {
                tell(player, "becom.link.short_of_cable", cost, InfoName.of(cable));
                return Outcome.NEEDS_CABLE;
            }
            cables.add(cable);
            model.setCables(a, b, cables);
            tell(player, "becom.link.cable_added", InfoName.of(cable), cables.size(), bundleSize);
            return Outcome.CABLE_ADDED;
        }
        // Best first, so the poorest is last.
        CableTier poorest = cables.get(cables.size() - 1);
        if (poorest.ordinal() >= cable.ordinal()) {
            tell(player, "becom.link.bundle_full", bundleSize);
            return Outcome.BUNDLE_FULL;
        }
        if (!take(player, cable, cost)) {
            tell(player, "becom.link.short_of_cable", cost, InfoName.of(cable));
            return Outcome.NEEDS_CABLE;
        }
        cables.set(cables.size() - 1, cable);
        model.setCables(a, b, cables);
        give(player, poorest, cost);
        tell(player, "becom.link.cable_upgraded", InfoName.of(poorest), InfoName.of(cable));
        return Outcome.CABLE_UPGRADED;
    }

    /** How many cable items a span between two nodes takes: one for every few blocks of it, at least one. */
    public int cost(NetNode a, NetNode b) {
        return Math.max(1, (int) Math.ceil(a.distanceTo(b) / blocksPerItem));
    }

    /** The first cable in the player's hotbar, left to right, or null. */
    private static CableTier hotbarCable(EntityPlayer player) {
        for (int slot = 0; slot < 9; slot++) {
            CableTier tier = BlockPhoneCable.tierOf(player.inventory.mainInventory[slot]);
            if (tier != null) return tier;
        }
        return null;
    }

    /** Takes {@code count} cables of a tier from the player's inventory, or nothing when they have fewer. */
    private static boolean take(EntityPlayer player, CableTier tier, int count) {
        if (player.capabilities.isCreativeMode) return true;
        ItemStack[] inventory = player.inventory.mainInventory;
        int have = 0;
        for (ItemStack stack : inventory) if (BlockPhoneCable.tierOf(stack) == tier) have += stack.stackSize;
        if (have < count) return false;
        int left = count;
        for (int slot = 0; slot < inventory.length && left > 0; slot++) {
            ItemStack stack = inventory[slot];
            if (BlockPhoneCable.tierOf(stack) != tier) continue;
            int used = Math.min(left, stack.stackSize);
            stack.stackSize -= used;
            left -= used;
            if (stack.stackSize <= 0) inventory[slot] = null;
        }
        player.inventoryContainer.detectAndSendChanges();
        return true;
    }

    /** Gives a player cables back, dropping at their feet what doesn't fit. */
    private static void give(EntityPlayer player, CableTier tier, int count) {
        if (player.capabilities.isCreativeMode) return;
        for (ItemStack stack : stacks(tier, count)) {
            if (!player.inventory.addItemStackToInventory(stack)) player.dropPlayerItemWithRandomChoice(stack, false);
        }
        player.inventoryContainer.detectAndSendChanges();
    }

    private static List<ItemStack> stacks(CableTier tier, int count) {
        List<ItemStack> stacks = new ArrayList<>();
        int max = BlockPhoneCable.stack(tier, 1)
            .getMaxStackSize();
        for (int left = count; left > 0; left -= max) stacks.add(BlockPhoneCable.stack(tier, Math.min(left, max)));
        return stacks;
    }

    /** A node's machine is broken: the cables on every span it held fall where it stood. */
    public void dropCables(World world, long pos) {
        NetworkModel model = network.model(world);
        NetNode node = model.node(pos);
        if (node == null) return;
        for (long other : node.links()) {
            NetNode far = model.node(other);
            if (far == null) continue;
            int cost = cost(node, far);
            for (CableTier tier : model.cables(pos, other)) {
                for (ItemStack stack : stacks(tier, cost)) {
                    world.spawnEntityInWorld(
                        new EntityItem(
                            world,
                            BlockPos.x(pos) + 0.5,
                            BlockPos.y(pos) + 0.5,
                            BlockPos.z(pos) + 0.5,
                            stack));
                }
            }
        }
    }

    /** A cable tier's name as a chat argument, translated by the reader. */
    private static final class InfoName {

        static ChatComponentTranslation of(CableTier tier) {
            return new ChatComponentTranslation("tile.becom." + BlockPhoneCable.nameOf(tier) + ".name");
        }
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
