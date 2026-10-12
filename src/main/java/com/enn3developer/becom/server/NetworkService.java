package com.enn3developer.becom.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

import com.enn3developer.becom.BeCom;
import com.enn3developer.becom.data.NetworkSavedData;
import com.enn3developer.becom.logic.LineModel;
import com.enn3developer.becom.logic.Sweep;
import com.enn3developer.becom.logic.Tunables;
import com.enn3developer.becom.logic.net.BlockPos;
import com.enn3developer.becom.logic.net.CallGraph;
import com.enn3developer.becom.logic.net.Clearance;
import com.enn3developer.becom.logic.net.LinkRules;
import com.enn3developer.becom.logic.net.NetNode;
import com.enn3developer.becom.logic.net.NetworkModel;
import com.enn3developer.becom.logic.net.NodeKind;
import com.enn3developer.becom.logic.net.NodeRef;
import com.enn3developer.becom.logic.net.WiredTopology;
import com.enn3developer.becom.logic.radio.AntennaNetwork;
import com.enn3developer.becom.packet.BeComPackets;
import com.enn3developer.becom.packet.WireSyncPacket;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * The live side of every loaded dimension's network (DESIGN.md 14): which powered nodes are working, which phone hangs
 * off which terminal, the clearance re-checks (D20), the graph calls are routed over, and the wires clients draw.
 * Server thread only.
 */
public final class NetworkService {

    // A powered node not heard from for 2 seconds counts as offline (DESIGN.md 14).
    private static final long OFFLINE_AFTER_TICKS = 40;
    private static final int TICKS_PER_SECOND = 20;

    private final Tunables tunables;
    private final LinkRules rules;
    private final LineModel line;
    private final Map<Integer, NetworkModel> models = new HashMap<>();
    private final Map<NodeRef, Long> lastHeard = new HashMap<>();
    private final Map<NodeRef, Integer> attachments = new HashMap<>();
    private final Map<NodeRef, Integer> phonesSeen = new HashMap<>();
    private final Map<Integer, Long> seenModelVersions = new HashMap<>();
    private final Map<Integer, Long> wiresSent = new HashMap<>();
    private long tick;
    private long version;
    private long nextClearanceTick;
    private final Sweep<Recheck> recheck = new Sweep<>();
    private final AntennaNetwork antennas;
    private long antennasVersion = -1;
    private CallGraph graph;
    private long graphVersion = -1;

    /** @param antennas where calls find the UHF links between antennas (DESIGN.md 7) */
    public NetworkService(Tunables tunables, AntennaNetwork antennas) {
        this.tunables = tunables;
        this.antennas = antennas;
        this.rules = new LinkRules(tunables.pylonSpanFactor(), tunables.wrenchLinkDistance());
        this.line = tunables.lineModel();
        this.nextClearanceTick = (long) tunables.pylonClearanceSeconds() * TICKS_PER_SECOND;
    }

    public LinkRules rules() {
        return rules;
    }

    /** Server ticks since BeCom started; the clock heartbeats and half-made links are measured in. */
    public long currentTick() {
        return tick;
    }

    // region Models

    /** Loads a dimension's network as its world loads, which also stops the server on data that is too new. */
    public void worldLoaded(World world) {
        if (world.isRemote) return;
        models.put(
            world.provider.dimensionId,
            NetworkSavedData.get(world)
                .model());
        version++;
    }

    public void worldUnloaded(World world) {
        if (world.isRemote) return;
        int dimension = world.provider.dimensionId;
        models.remove(dimension);
        lastHeard.keySet()
            .removeIf(ref -> ref.dimension() == dimension);
        attachments.keySet()
            .removeIf(ref -> ref.dimension() == dimension);
        phonesSeen.keySet()
            .removeIf(ref -> ref.dimension() == dimension);
        wiresSent.remove(dimension);
        version++;
    }

    /** The network of a loaded dimension, or null. */
    public NetworkModel model(int dimension) {
        return models.get(dimension);
    }

    public NetworkModel model(World world) {
        NetworkModel model = models.get(world.provider.dimensionId);
        if (model == null) {
            worldLoaded(world);
            model = models.get(world.provider.dimensionId);
        }
        return model;
    }

    // endregion

    // region Nodes

    /** The node a machine at {@code pos} stands for, made when it's placed (or found missing). */
    public NetNode ensureNode(World world, long pos, NodeKind kind, UUID owner) {
        NetworkModel model = model(world);
        NetNode node = model.node(pos);
        if (node != null && node.kind() == kind) {
            if (node.owner() == null && owner != null) {
                node.setOwner(owner);
                model.changed();
            }
            return node;
        }
        node = new NetNode(pos, kind, owner);
        node.setFormed(!kind.structure());
        model.add(node);
        return node;
    }

    /** The machine was broken: its node and every link to it go (DESIGN.md 14). */
    public void removeNode(World world, long pos) {
        NetworkModel model = model(world);
        model.remove(pos);
        NodeRef ref = new NodeRef(world.provider.dimensionId, pos);
        lastHeard.remove(ref);
        attachments.remove(ref);
        phonesSeen.remove(ref);
        version++;
    }

    /** A pylon head finished its structure check. A newly formed pylon links on its own if it can (DESIGN.md 5.1). */
    public void setFormed(World world, long pos, boolean formed, int height) {
        NetworkModel model = model(world);
        NetNode node = model.node(pos);
        if (node == null || node.formed() == formed && node.height() == height) return;
        boolean wasFormed = node.formed();
        node.setFormed(formed);
        node.setHeight(height);
        model.changed();
        if (formed && !wasFormed
            && node.kind()
                .pylonHead()) {
            NetNode target = rules
                .autoLinkTarget(model, node, (a, b) -> clearance(world, a.pos(), b.pos()) != Clearance.Result.BLOCKED);
            if (target != null) {
                model.link(node.pos(), target.pos());
                BeCom.LOG.debug(
                    "Pylon at {} linked on its own to {}",
                    BlockPos.toString(pos),
                    BlockPos.toString(target.pos()));
            }
        }
    }

    /** A powered node is formed, loaded and powered this tick (D11). */
    public void heartbeat(World world, long pos) {
        Long before = lastHeard.put(new NodeRef(world.provider.dimensionId, pos), tick);
        if (before == null) version++;
    }

    public boolean working(int dimension, long pos) {
        Long heard = lastHeard.get(new NodeRef(dimension, pos));
        return heard != null && tick - heard <= OFFLINE_AFTER_TICKS;
    }

    /**
     * What a terminal's last cable scan found: the one phone it serves, or none when it found no phone or more than
     * one (D19).
     */
    public void attach(World world, long terminalPos, int phone, int phonesFound) {
        NodeRef ref = new NodeRef(world.provider.dimensionId, terminalPos);
        phonesSeen.put(ref, phonesFound);
        Integer before = phone < 0 ? attachments.remove(ref) : attachments.put(ref, phone);
        if (before == null ? phone >= 0 : before != phone) version++;
    }

    /** The number of phones a terminal's last scan saw on its cable. */
    public int phonesSeen(int dimension, long terminalPos) {
        return phonesSeen.getOrDefault(new NodeRef(dimension, terminalPos), 0);
    }

    /** How many terminals found {@code phone} on their cable; a phone on more than one is served by none (D19). */
    public int terminalsOf(int phone) {
        int found = 0;
        for (int attached : attachments.values()) if (attached == phone) found++;
        return found;
    }

    // endregion

    // region Paths

    /** The graph calls are routed over, rebuilt only when something it depends on changed. */
    public CallGraph graph() {
        long now = version();
        if (graph == null || now != graphVersion) {
            graph = build();
            graphVersion = now;
        }
        return graph;
    }

    /** Goes up on every change that can move a path: nodes, links, clearance, power, attachments. */
    public long version() {
        boolean changed = seenModelVersions.size() != models.size() || antennas.version() != antennasVersion;
        antennasVersion = antennas.version();
        for (Map.Entry<Integer, NetworkModel> entry : models.entrySet()) {
            Long seen = seenModelVersions.put(
                entry.getKey(),
                entry.getValue()
                    .version());
            if (seen == null || seen != entry.getValue()
                .version()) changed = true;
        }
        if (changed) {
            seenModelVersions.keySet()
                .retainAll(models.keySet());
            version++;
        }
        return version;
    }

    private CallGraph build() {
        CallGraph.Builder builder = CallGraph.builder();
        for (Map.Entry<Integer, NetworkModel> entry : models.entrySet()) {
            int dimension = entry.getKey();
            for (WiredTopology.Segment segment : WiredTopology
                .segments(entry.getValue(), pos -> working(dimension, pos), line, rules)) {
                builder.hop(
                    new CallGraph.NodeVertex(dimension, segment.from()),
                    new CallGraph.NodeVertex(dimension, segment.to()),
                    segment.snr(),
                    CallGraph.HopKind.LINE);
            }
            // Calls travel between antennas on UHF (DESIGN.md 7), once both are working.
            antennas.forEachUhfLink(dimension, (a, b, snr) -> {
                if (working(dimension, a) && working(dimension, b)) {
                    builder.twoWay(
                        new CallGraph.NodeVertex(dimension, a),
                        new CallGraph.NodeVertex(dimension, b),
                        snr,
                        CallGraph.HopKind.RADIO);
                }
            });
        }
        Map<Integer, Integer> terminalsPerPhone = new HashMap<>();
        for (int phone : attachments.values()) terminalsPerPhone.merge(phone, 1, Integer::sum);
        for (Map.Entry<NodeRef, Integer> entry : attachments.entrySet()) {
            NodeRef terminal = entry.getKey();
            if (!working(terminal.dimension(), terminal.pos())) continue;
            // Strictly one phone per terminal and one terminal per phone (D19): a phone on two cables gets neither.
            if (terminalsPerPhone.get(entry.getValue()) > 1) continue;
            // Phone Cable is lossless (DESIGN.md 5.5).
            builder.twoWay(
                new CallGraph.PhoneVertex(entry.getValue()),
                new CallGraph.NodeVertex(terminal.dimension(), terminal.pos()),
                Double.POSITIVE_INFINITY,
                CallGraph.HopKind.CABLE);
        }
        return builder.build();
    }

    // endregion

    // region Clearance

    /** Whether the line between two heads is clear (D20), probing only loaded chunks. */
    public Clearance.Result clearance(World world, long a, long b) {
        return Clearance.check(a, b, (x, y, z) -> probe(world, x, y, z));
    }

    private static Clearance.Probe probe(World world, int x, int y, int z) {
        if (y < 0 || y >= world.getHeight()) return Clearance.Probe.AIR;
        if (!world.getChunkProvider()
            .chunkExists(x >> 4, z >> 4)) return Clearance.Probe.UNLOADED;
        Block block = world.getBlock(x, y, z);
        return block.getMaterial()
            .blocksMovement() ? Clearance.Probe.SOLID : Clearance.Probe.AIR;
    }

    /** Re-checks every pylon link in loaded chunks, and drops nodes whose machine is gone. */
    public void recheckAll() {
        queueRecheck();
        recheck.run(this::recheck, System::nanoTime, Long.MAX_VALUE);
    }

    /** Queues every node and pylon link of every loaded dimension for the next round of re-checks. */
    private void queueRecheck() {
        List<Recheck> round = new ArrayList<>();
        for (Map.Entry<Integer, NetworkModel> entry : models.entrySet()) {
            int dimension = entry.getKey();
            for (NetNode node : entry.getValue()
                .nodes()) round.add(new Recheck(dimension, node.pos(), Long.MIN_VALUE));
            for (NetworkModel.LinkKey key : entry.getValue()
                .links()) round.add(new Recheck(dimension, key.a(), key.b()));
        }
        recheck.add(round);
    }

    /** One item of a re-check round: a node to look for, or a link to walk ({@code b} set). */
    private record Recheck(int dimension, long a, long b) {}

    private void recheck(Recheck item) {
        WorldServer world = DimensionManager.getWorld(item.dimension());
        NetworkModel model = models.get(item.dimension());
        if (world == null || model == null) return;
        if (item.b() == Long.MIN_VALUE) {
            NetNode node = model.node(item.a());
            if (node != null && isStale(world, node)) {
                BeCom.LOG.info(
                    "Dropping network node at {} in dimension {}: its machine is gone",
                    BlockPos.toString(node.pos()),
                    world.provider.dimensionId);
                removeNode(world, node.pos());
            }
            return;
        }
        NetNode a = model.node(item.a());
        NetNode b = model.node(item.b());
        if (a == null || b == null
            || !model.linked(item.a(), item.b())
            || !a.kind()
                .pylonHead()
            || !b.kind()
                .pylonHead())
            return;
        Clearance.Result result = clearance(world, item.a(), item.b());
        if (result == Clearance.Result.UNKNOWN) return;
        model.setBlocked(item.a(), item.b(), result == Clearance.Result.BLOCKED);
    }

    /** A node whose machine is gone from a loaded chunk; unloaded ones are left alone. */
    private static boolean isStale(World world, NetNode node) {
        if (!world.getChunkProvider()
            .chunkExists(node.x() >> 4, node.z() >> 4)) return false;
        TileEntity tile = world.getTileEntity(node.x(), node.y(), node.z());
        return !(tile instanceof IGregTechTileEntity gt && gt.getMetaTileEntity() instanceof NodeHost host
            && host.nodeKind() == node.kind());
    }

    // endregion

    // region Ticking and wires

    /** One server tick; line re-checks get at most {@code recheckBudgetNanos} of it. */
    public void tick(long recheckBudgetNanos) {
        tick++;
        if (tick % TICKS_PER_SECOND == 0) {
            expireOffline();
            syncWires();
        }
        if (tick >= nextClearanceTick) {
            nextClearanceTick = tick + (long) tunables.pylonClearanceSeconds() * TICKS_PER_SECOND;
            // A round still running from last time finishes first rather than piling up.
            if (recheck.isEmpty()) queueRecheck();
        }
        recheck.run(this::recheck, System::nanoTime, recheckBudgetNanos);
    }

    private void expireOffline() {
        Iterator<Map.Entry<NodeRef, Long>> it = lastHeard.entrySet()
            .iterator();
        while (it.hasNext()) {
            if (tick - it.next()
                .getValue() > OFFLINE_AFTER_TICKS) {
                it.remove();
                version++;
            }
        }
    }

    private void syncWires() {
        for (Map.Entry<Integer, NetworkModel> entry : models.entrySet()) {
            long modelVersion = entry.getValue()
                .version();
            Long sent = wiresSent.put(entry.getKey(), modelVersion);
            if (sent != null && sent == modelVersion) continue;
            BeComPackets.sendToDimension(wires(entry.getKey()), entry.getKey());
        }
    }

    /** Sends a player the wires of the dimension they're in, as they log in or arrive. */
    public void sendWires(EntityPlayerMP player) {
        BeComPackets.send(wires(player.dimension), player);
    }

    private WireSyncPacket wires(int dimension) {
        List<WireSyncPacket.Wire> wires = new ArrayList<>();
        NetworkModel model = models.get(dimension);
        if (model != null) {
            for (NetworkModel.LinkKey key : model.links()) {
                NetNode a = model.node(key.a());
                NetNode b = model.node(key.b());
                // A wire hangs broken while blocked, or while its pylons are too short for its span.
                boolean down = model.isBlocked(key.a(), key.b())
                    || a != null && b != null && a.distanceTo(b) > rules.maxSpan(a, b);
                wires.add(new WireSyncPacket.Wire(key.a(), key.b(), down));
            }
        }
        return new WireSyncPacket(dimension, wires);
    }

    // endregion
}
