package com.enn3developer.becom.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import com.enn3developer.becom.logic.space.Gateways;
import com.enn3developer.becom.logic.space.Portals;
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
    // A dish's loss moves calls' paths in steps this small; finer ones would rebuild them every second of a fade.
    private static final double LOSS_STEP = 0.1;

    private final Tunables tunables;
    private final LinkRules rules;
    private final LineModel line;
    private final Map<Integer, NetworkModel> models = new HashMap<>();
    private final Map<NodeRef, Long> lastHeard = new HashMap<>();
    private final Map<NodeRef, Integer> attachments = new HashMap<>();
    private final Map<NodeRef, Integer> phonesSeen = new HashMap<>();
    // Each working ME Bridge's ME network, by grid id (DESIGN.md 8.3).
    private final Map<NodeRef, Integer> meBridges = new HashMap<>();
    // Each working Satellite Dish, and what its hop up to the satellites loses (DESIGN.md 9.2).
    private final Map<NodeRef, Double> dishes = new HashMap<>();
    // Each Rocket Radio Receiver standing on a pad, by its rocket's spot; working while heard (DESIGN.md 9.3).
    private final Set<NodeRef> receivers = new HashSet<>();
    // Each working Portal Relay, and the kinds of portal in its reach (DESIGN.md 9.4).
    private final Map<NodeRef, Set<Portals.Kind>> relays = new HashMap<>();
    private final Map<Integer, Long> seenModelVersions = new HashMap<>();
    private final Map<Integer, Long> wiresSent = new HashMap<>();
    private long tick;
    private long version;
    private long nextClearanceTick;
    private final Sweep<Recheck> recheck = new Sweep<>();
    private final AntennaNetwork antennas;
    private long antennasVersion = -1;
    private final SpaceService space;
    private final Gateways gateways;
    private long spaceVersion = -1;
    private CallGraph graph;
    private long graphVersion = -1;

    /**
     * @param antennas where calls find the UHF links between antennas (DESIGN.md 7)
     * @param space    the satellites, which carry calls between dishes and up from planets (DESIGN.md 9)
     */
    public NetworkService(Tunables tunables, AntennaNetwork antennas, SpaceService space) {
        this.tunables = tunables;
        this.antennas = antennas;
        this.space = space;
        this.gateways = Gateways.of(tunables);
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
        meBridges.keySet()
            .removeIf(ref -> ref.dimension() == dimension);
        dishes.keySet()
            .removeIf(ref -> ref.dimension() == dimension);
        receivers.removeIf(ref -> ref.dimension() == dimension);
        relays.keySet()
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

    /** The machine was broken: its node and every link to it go (DESIGN.md 14), a relay's partner included. */
    public void removeNode(World world, long pos) {
        NetworkModel model = model(world);
        NodeRef ref = new NodeRef(world.provider.dimensionId, pos);
        NetNode node = model.node(pos);
        if (node != null && node.partner() != null) forgetPartner(node.partner(), ref);
        model.remove(pos);
        lastHeard.remove(ref);
        attachments.remove(ref);
        phonesSeen.remove(ref);
        meBridges.remove(ref);
        dishes.remove(ref);
        relays.remove(ref);
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

    /** An ME Bridge is online in ME network {@code grid} this tick: calls cross between the two (DESIGN.md 8.3). */
    public void meBridge(World world, long pos, int grid) {
        Integer before = meBridges.put(new NodeRef(world.provider.dimensionId, pos), grid);
        if (before == null || before != grid) version++;
    }

    /** An ME Bridge is offline, in BeCom's network or its ME network. */
    public void meBridgeOff(World world, long pos) {
        if (meBridges.remove(new NodeRef(world.provider.dimensionId, pos)) != null) version++;
    }

    /**
     * A Satellite Dish is working this tick, under open sky (DESIGN.md 9.2): its hop up loses {@code loss} dB, for
     * rain at the dish and its maintenance problems.
     */
    public void dish(World world, long pos, double loss) {
        double rounded = Math.round(loss / LOSS_STEP) * LOSS_STEP;
        Double before = dishes.put(new NodeRef(world.provider.dimensionId, pos), rounded);
        if (before == null || before != rounded) version++;
    }

    /** A Satellite Dish lost its sky, its structure or its power. */
    public void dishOff(World world, long pos) {
        if (dishes.remove(new NodeRef(world.provider.dimensionId, pos)) != null) version++;
    }

    /**
     * A Rocket Radio Receiver counts this second, in a rocket standing at {@code pos} (DESIGN.md 9.3): it uplinks to
     * the Overworld's satellites while it's heard from.
     */
    public void receiver(World world, long pos) {
        heartbeat(world, pos);
        if (receivers.add(new NodeRef(world.provider.dimensionId, pos))) version++;
    }

    /** The receiver's rocket launched, left its pad, or lost its receiver. */
    public void receiverGone(int dimension, long pos) {
        NodeRef ref = new NodeRef(dimension, pos);
        if (receivers.remove(ref)) version++;
        if (lastHeard.remove(ref) != null) version++;
    }

    /** A Portal Relay is powered this tick, with these kinds of portal in its reach (DESIGN.md 9.4). */
    public void relay(World world, long pos, Set<Portals.Kind> portals) {
        heartbeat(world, pos);
        Set<Portals.Kind> before = relays.put(new NodeRef(world.provider.dimensionId, pos), portals);
        if (!portals.equals(before)) version++;
    }

    /** A Portal Relay lost its power, or every portal in its reach. */
    public void relayOff(World world, long pos) {
        if (relays.remove(new NodeRef(world.provider.dimensionId, pos)) != null) version++;
    }

    /** The relay at {@code ref}'s partner, when both are relays of loaded dimensions paired with each other. */
    public NodeRef partnerOf(NodeRef ref) {
        NetNode node = relayAt(ref);
        if (node == null || node.partner() == null) return null;
        NetNode other = relayAt(node.partner());
        return other != null && ref.equals(other.partner()) ? node.partner() : null;
    }

    private NetNode relayAt(NodeRef ref) {
        NetworkModel model = models.get(ref.dimension());
        NetNode node = model == null ? null : model.node(ref.pos());
        return node != null && node.kind() == NodeKind.PORTAL_RELAY ? node : null;
    }

    /** Pairs two Portal Relays through their portal (DESIGN.md 9.4); the link service checks who may. */
    public void pair(NodeRef a, NodeRef b) {
        NetNode nodeA = relayAt(a);
        NetNode nodeB = relayAt(b);
        if (nodeA == null || nodeB == null) return;
        if (nodeA.partner() != null && !nodeA.partner()
            .equals(b)) forgetPartner(nodeA.partner(), a);
        if (nodeB.partner() != null && !nodeB.partner()
            .equals(a)) forgetPartner(nodeB.partner(), b);
        nodeA.setPartner(b);
        nodeB.setPartner(a);
        models.get(a.dimension())
            .changed();
        models.get(b.dimension())
            .changed();
    }

    /** Unpairs two Portal Relays paired with each other. */
    public void unpair(NodeRef a, NodeRef b) {
        forgetPartner(a, b);
        forgetPartner(b, a);
    }

    /** The relay at {@code ref} forgets {@code partner}, if it's loaded and paired with it. */
    private void forgetPartner(NodeRef ref, NodeRef partner) {
        NetNode node = relayAt(ref);
        if (node == null || !partner.equals(node.partner())) return;
        node.setPartner(null);
        models.get(ref.dimension())
            .changed();
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
        boolean changed = seenModelVersions.size() != models.size() || antennas.version() != antennasVersion
            || space.version() != spaceVersion;
        antennasVersion = antennas.version();
        spaceVersion = space.version();
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
        // An ME Bridge joins its ME network to the line it bridges, losing nothing (DESIGN.md 8.3: a Max hop).
        for (Map.Entry<NodeRef, Integer> entry : meBridges.entrySet()) {
            NodeRef bridge = entry.getKey();
            if (!working(bridge.dimension(), bridge.pos())) continue;
            builder.twoWay(
                new CallGraph.MeVertex(entry.getValue(), bridge.dimension()),
                new CallGraph.NodeVertex(bridge.dimension(), bridge.pos()),
                Double.POSITIVE_INFINITY,
                CallGraph.HopKind.ME_BRIDGE);
        }
        gateways.addTo(builder, this::satellitesOver, workingDishes(), workingReceivers(), workingRelayPairs());
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

    /** The satellites over a dimension that its sky hops can reach: none where it has no sky (DESIGN.md 6.1). */
    private int satellitesOver(int dimension) {
        return space.hasSky(dimension) ? space.count(dimension) : 0;
    }

    private List<Gateways.Dish> workingDishes() {
        List<Gateways.Dish> working = new ArrayList<>();
        for (Map.Entry<NodeRef, Double> entry : dishes.entrySet()) {
            NodeRef dish = entry.getKey();
            if (working(dish.dimension(), dish.pos())) {
                working.add(new Gateways.Dish(dish.dimension(), dish.pos(), entry.getValue()));
            }
        }
        return working;
    }

    /**
     * Every pair of working Portal Relays, once each: both in reach of the portal between their dimensions (DESIGN.md
     * 9.4), one end in the Overworld (9.5).
     */
    private List<Gateways.RelayPair> workingRelayPairs() {
        List<Gateways.RelayPair> pairs = new ArrayList<>();
        for (Map.Entry<NodeRef, Set<Portals.Kind>> entry : relays.entrySet()) {
            NodeRef a = entry.getKey();
            NodeRef b = partnerOf(a);
            // Each pair once, from the end that sorts first.
            if (b == null || compare(a, b) >= 0) continue;
            if (!working(a.dimension(), a.pos()) || !working(b.dimension(), b.pos())) continue;
            Portals.Kind portal = Portals
                .pairKind(a.dimension(), providerOf(a.dimension()), b.dimension(), providerOf(b.dimension()));
            Set<Portals.Kind> atB = relays.get(b);
            if (portal == null || !entry.getValue()
                .contains(portal) || atB == null || !atB.contains(portal)) continue;
            pairs.add(new Gateways.RelayPair(a.dimension(), a.pos(), b.dimension(), b.pos()));
        }
        return pairs;
    }

    private static int compare(NodeRef a, NodeRef b) {
        int byDimension = Integer.compare(a.dimension(), b.dimension());
        return byDimension != 0 ? byDimension : Long.compare(a.pos(), b.pos());
    }

    /** The class of a loaded dimension's world provider, which tells the Twilight Forest and Personal Space apart. */
    public static String providerOf(int dimension) {
        WorldServer world = DimensionManager.getWorld(dimension);
        return world == null ? null
            : world.provider.getClass()
                .getName();
    }

    private List<Gateways.Receiver> workingReceivers() {
        List<Gateways.Receiver> working = new ArrayList<>();
        for (NodeRef receiver : receivers) {
            if (working(receiver.dimension(), receiver.pos())) {
                working.add(new Gateways.Receiver(receiver.dimension(), receiver.pos()));
            }
        }
        return working;
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
