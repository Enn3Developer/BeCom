package com.enn3developer.becom.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

import com.enn3developer.becom.BeCom;
import com.enn3developer.becom.block.CableBuses;
import com.enn3developer.becom.data.NetworkSavedData;
import com.enn3developer.becom.logic.LevelScale;
import com.enn3developer.becom.logic.Sweep;
import com.enn3developer.becom.logic.Tunables;
import com.enn3developer.becom.logic.net.BlockPos;
import com.enn3developer.becom.logic.net.Buses;
import com.enn3developer.becom.logic.net.CableTier;
import com.enn3developer.becom.logic.net.CallGraph;
import com.enn3developer.becom.logic.net.NetNode;
import com.enn3developer.becom.logic.net.NetworkModel;
import com.enn3developer.becom.logic.net.NodeKind;
import com.enn3developer.becom.logic.net.NodeRef;
import com.enn3developer.becom.logic.radio.AntennaNetwork;
import com.enn3developer.becom.logic.space.Gateways;
import com.enn3developer.becom.logic.space.Portals;
import com.enn3developer.becom.quest.BeComQuests;
import com.enn3developer.becom.quest.QuestTask;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * The live side of every loaded dimension's network: which powered nodes are working, the cable buses and what each
 * reaches, how many calls each Exchange switches, the sweep that drops nodes whose machine is gone, and the graph calls
 * are routed over. Server thread only.
 */
public final class NetworkService {

    // A powered node not heard from for 2 seconds counts as offline.
    private static final long OFFLINE_AFTER_TICKS = 40;
    private static final int TICKS_PER_SECOND = 20;
    // A dish's loss moves calls' paths in steps this small; finer ones would rebuild them every second of a fade.
    private static final double LOSS_STEP = 0.1;
    // Every node is looked for this often, so one whose machine went without telling the network goes too.
    private static final int SWEEP_SECONDS = 60;

    private final Tunables tunables;
    private final LevelScale levels;
    private final Map<Integer, NetworkModel> models = new HashMap<>();
    private final Map<NodeRef, Long> lastHeard = new HashMap<>();
    // Each loaded dimension's cable buses, worked out again where something changed.
    private final Map<Integer, Buses> buses = new HashMap<>();
    // Each wired phone's buses, by number, indexed again whenever a bus changes.
    private Map<Integer, List<BusRef>> phoneBuses = Map.of();
    // How many calls each working Exchange's racks switch at once.
    private final Map<NodeRef, Integer> exchanges = new HashMap<>();
    // Each working ME Bridge's ME network, by grid id.
    private final Map<NodeRef, Integer> meBridges = new HashMap<>();
    // Each working Satellite Dish, and what its hop up to the satellites loses.
    private final Map<NodeRef, Double> dishes = new HashMap<>();
    // Each Rocket Radio Receiver standing on a pad, by its rocket's spot; working while heard.
    private final Set<NodeRef> receivers = new HashSet<>();
    // Each working Portal Relay, and the kinds of portal in its reach.
    private final Map<NodeRef, Set<Portals.Kind>> relays = new HashMap<>();
    // Each Portal Relay's GT voltage tier, as it last said.
    private final Map<NodeRef, Integer> relayTiers = new HashMap<>();
    // Where the Personal Space portals by each relay lead, at its last look, working or not.
    private final Map<NodeRef, Set<Integer>> personalTargets = new HashMap<>();
    private final Map<Integer, Long> seenModelVersions = new HashMap<>();
    private long tick;
    private long version;
    private long nextSweepTick = (long) SWEEP_SECONDS * TICKS_PER_SECOND;
    private final Sweep<NodeRef> sweep = new Sweep<>();
    private final AntennaNetwork antennas;
    private long antennasVersion = -1;
    private final SpaceService space;
    private final Gateways gateways;
    private long spaceVersion = -1;
    private CallGraph graph;
    private long graphVersion = -1;

    /**
     * @param antennas where calls find the UHF links between antennas
     * @param space    the satellites, which carry calls between dishes and up from planets
     */
    public NetworkService(Tunables tunables, AntennaNetwork antennas, SpaceService space) {
        this.tunables = tunables;
        this.antennas = antennas;
        this.space = space;
        this.gateways = Gateways.of(tunables);
        this.levels = tunables.levelScale();
    }

    /** Server ticks since BeCom started; the clock heartbeats are measured in. */
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
        if (buses.remove(dimension) != null) indexPhones();
        exchanges.keySet()
            .removeIf(ref -> ref.dimension() == dimension);
        routerTiers.keySet()
            .removeIf(ref -> ref.dimension() == dimension);
        meBridges.keySet()
            .removeIf(ref -> ref.dimension() == dimension);
        dishes.keySet()
            .removeIf(ref -> ref.dimension() == dimension);
        receivers.removeIf(ref -> ref.dimension() == dimension);
        relays.keySet()
            .removeIf(ref -> ref.dimension() == dimension);
        relayTiers.keySet()
            .removeIf(ref -> ref.dimension() == dimension);
        personalTargets.keySet()
            .removeIf(ref -> ref.dimension() == dimension);
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
        model.add(node);
        return node;
    }

    /** The machine was broken: its node goes, and a relay's partner forgets it. */
    public void removeNode(World world, long pos) {
        NetworkModel model = model(world);
        NodeRef ref = new NodeRef(world.provider.dimensionId, pos);
        NetNode node = model.node(pos);
        if (node != null && node.partner() != null) forgetPartner(node.partner(), ref);
        model.remove(pos);
        lastHeard.remove(ref);
        exchanges.remove(ref);
        routerTiers.remove(ref);
        meBridges.remove(ref);
        dishes.remove(ref);
        relays.remove(ref);
        relayTiers.remove(ref);
        personalTargets.remove(ref);
        version++;
    }

    /** A working Exchange's racks switch {@code calls} calls at once this tick. */
    public void exchange(World world, long pos, int calls) {
        heartbeat(world, pos);
        Integer before = exchanges.put(new NodeRef(world.provider.dimensionId, pos), calls);
        if (before == null || before != calls) version++;
    }

    /** Every working Exchange. */
    public List<NodeRef> exchanges() {
        List<NodeRef> working = new ArrayList<>();
        for (NodeRef exchange : exchanges.keySet())
            if (working(exchange.dimension(), exchange.pos())) working.add(exchange);
        return working;
    }

    /** The calls a working Exchange's racks switch at once; 0 for one not heard from. */
    public int exchangeCalls(int dimension, long pos) {
        return working(dimension, pos) ? exchanges.getOrDefault(new NodeRef(dimension, pos), 0) : 0;
    }

    /** A powered node is formed, loaded and powered this tick. */
    public void heartbeat(World world, long pos) {
        Long before = lastHeard.put(new NodeRef(world.provider.dimensionId, pos), tick);
        if (before == null) version++;
    }

    /** An ME Bridge is online in ME network {@code grid} this tick: calls cross between the two. */
    public void meBridge(World world, long pos, int grid) {
        Integer before = meBridges.put(new NodeRef(world.provider.dimensionId, pos), grid);
        if (before == null || before != grid) version++;
    }

    /** An ME Bridge is offline, in BeCom's network or its ME network. */
    public void meBridgeOff(World world, long pos) {
        if (meBridges.remove(new NodeRef(world.provider.dimensionId, pos)) != null) version++;
    }

    /**
     * A Satellite Dish is working this tick, under open sky: its hop up loses {@code loss} dB, for
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
     * A Rocket Radio Receiver counts this second, in a rocket standing at {@code pos}: it uplinks to
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

    /**
     * A Portal Relay of GT voltage tier {@code tier} is powered this tick, with these kinds of portal in its reach.
     */
    public void relay(World world, long pos, Set<Portals.Kind> portals, int tier) {
        heartbeat(world, pos);
        NodeRef ref = new NodeRef(world.provider.dimensionId, pos);
        Set<Portals.Kind> before = relays.put(ref, portals);
        Integer tierBefore = relayTiers.put(ref, tier);
        if (!portals.equals(before) || tierBefore == null || tierBefore != tier) version++;
    }

    /** The GT voltage tier a relay pair works at: its lower relay's. */
    public int pairTier(NodeRef a, NodeRef b) {
        return Math.min(
            relayTiers.getOrDefault(a, Tunables.RELAY_MIN_TIER),
            relayTiers.getOrDefault(b, Tunables.RELAY_MIN_TIER));
    }

    /** A Portal Relay looked again at where the Personal Space portals in its reach lead. */
    public void personalTargets(World world, long pos, Set<Integer> targets) {
        NodeRef ref = new NodeRef(world.provider.dimensionId, pos);
        Set<Integer> before = targets.isEmpty() ? personalTargets.remove(ref) : personalTargets.put(ref, targets);
        if (!targets.equals(before == null ? Set.of() : before)) version++;
    }

    /** Where the Personal Space portals by the relay at {@code ref} lead, at its last look. */
    public Set<Integer> personalTargetsOf(NodeRef ref) {
        return personalTargets.getOrDefault(ref, Set.of());
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

    /**
     * A working relay with no partner pairs with a relay by the far side of the portal in its reach: one with no
     * partner either, in a loaded dimension that portal leads to, standing where the portal comes out, by a portal of
     * the same kind when it last looked (it needn't be loaded now: its chunk may not be). A relay whose
     * partner's dimension isn't loaded keeps it, since it can't be told. Of several, the one that sorts first.
     */
    private void pairRelays() {
        double reach = tunables.relayPortalDistance();
        for (Map.Entry<NodeRef, Set<Portals.Kind>> entry : new ArrayList<>(relays.entrySet())) {
            NodeRef a = entry.getKey();
            if (!working(a.dimension(), a.pos()) || !unpaired(a)) continue;
            NodeRef best = null;
            for (Map.Entry<Integer, NetworkModel> other : models.entrySet()) {
                int dimension = other.getKey();
                if (dimension == a.dimension()) continue;
                Portals.Kind portal = Portals
                    .pairKind(a.dimension(), providerOf(a.dimension()), dimension, providerOf(dimension));
                if (portal == null || !entry.getValue()
                    .contains(portal)) continue;
                for (NetNode node : other.getValue()
                    .nodes()) {
                    if (node.kind() != NodeKind.PORTAL_RELAY) continue;
                    NodeRef b = new NodeRef(dimension, node.pos());
                    if (!unpaired(b) || best != null && compare(b, best) >= 0) continue;
                    Set<Portals.Kind> atB = relays.get(b);
                    if (atB == null || !atB.contains(portal)) continue;
                    if (!Portals.sameLink(
                        portal,
                        movementFactorOf(a.dimension()),
                        BlockPos.x(a.pos()),
                        BlockPos.z(a.pos()),
                        movementFactorOf(dimension),
                        node.x(),
                        node.z(),
                        reach)) continue;
                    NodeRef overworld = a.dimension() == Portals.OVERWORLD ? a : b;
                    NodeRef far = overworld == a ? b : a;
                    if (Portals.leadsInto(portal, personalTargetsOf(overworld), far.dimension())) best = b;
                }
            }
            if (best == null) continue;
            pair(a, best);
            List<UUID> owners = new ArrayList<>();
            for (NodeRef end : new NodeRef[] { a, best }) {
                UUID owner = relayAt(end).owner();
                if (owner != null && !owners.contains(owner)) owners.add(owner);
            }
            BeComQuests.complete(QuestTask.PORTAL_RELAY_PAIR, owners);
        }
    }

    /** A relay with no partner, or one whose partner, in a loaded dimension, isn't paired back with it. */
    private boolean unpaired(NodeRef ref) {
        NetNode node = relayAt(ref);
        if (node == null) return false;
        NodeRef partner = node.partner();
        if (partner == null) return true;
        return models.containsKey(partner.dimension()) && partnerOf(ref) == null;
    }

    /** Pairs two Portal Relays through their portal. */
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
        saveNow(a.dimension(), b.dimension());
    }

    /** Unpairs two Portal Relays paired with each other. */
    public void unpair(NodeRef a, NodeRef b) {
        forgetPartner(a, b);
        forgetPartner(b, a);
        saveNow(a.dimension(), b.dimension());
    }

    /**
     * Writes two dimensions' networks to disk now. A pair's two halves live in two dimensions' files, which Minecraft
     * saves at different times: a far side loaded for a pairing unloads, and saves, at once, while the near side waits
     * for the next autosave. Saving both together leaves no window where a crash keeps only one half. While either has
     * saving switched off, by /save-off or a backup copying the world, neither is written now. A world loaded since a
     * /save-off still saves, though, so its half can reach disk alone as it unloads, and a crash before the other's
     * next save keeps one half: the pair is lost, and pairs again.
     */
    private static void saveNow(int a, int b) {
        List<WorldServer> worlds = new ArrayList<>();
        for (int dimension : new int[] { a, b }) {
            WorldServer world = DimensionManager.getWorld(dimension);
            if (world == null) continue;
            if (world.levelSaving) return;
            worlds.add(world);
        }
        for (WorldServer world : worlds) world.perWorldStorage.saveAllData();
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

    // region Buses

    /** A bus of some dimension. */
    public record BusRef(int dimension, Buses.Bus bus) {}

    /** The link a bus shares its rate on, across every hop over it. */
    public record BusLink(int dimension, long bus) {}

    /** Something at a position changed that a bus may reach: a cable, or a block beside one. */
    public void busChanged(World world, long pos) {
        if (!world.isRemote) busChanged(world.provider.dimensionId, pos);
    }

    public void busChanged(int dimension, long pos) {
        buses.computeIfAbsent(dimension, d -> new Buses())
            .changed(pos);
    }

    /** A chunk loaded or unloaded: the buses touching it are worked out again. */
    public void chunkChanged(World world, int chunkX, int chunkZ) {
        if (world.isRemote) return;
        Buses of = buses.get(world.provider.dimensionId);
        if (of != null) of.chunkChanged(chunkX, chunkZ);
    }

    /** Works the buses out again wherever something changed; any that came out different moves paths. */
    private void updateBuses() {
        boolean changed = false;
        for (Map.Entry<Integer, Buses> entry : buses.entrySet()) {
            if (!entry.getValue()
                .pending()) continue;
            WorldServer world = DimensionManager.getWorld(entry.getKey());
            if (world != null && entry.getValue()
                .update(new CableBuses(world), tunables::cableReach)) changed = true;
        }
        if (!changed) return;
        indexPhones();
        version++;
    }

    private void indexPhones() {
        Map<Integer, List<BusRef>> index = new HashMap<>();
        for (Map.Entry<Integer, Buses> entry : buses.entrySet()) {
            for (Buses.Bus bus : entry.getValue()
                .all()) {
                for (Buses.End end : bus.ends(Buses.Kind.PHONE)) {
                    if (end.number() >= 0) index.computeIfAbsent(end.number(), k -> new ArrayList<>())
                        .add(new BusRef(entry.getKey(), bus));
                }
            }
        }
        phoneBuses = index;
    }

    /** The buses reaching the machine or device at a position. */
    public List<Buses.Bus> busesAt(int dimension, long pos) {
        updateBuses();
        Buses of = buses.get(dimension);
        return of == null ? List.of() : of.busesOf(pos);
    }

    /** The bus a cable block is on, or null. */
    public Buses.Bus busOfCable(int dimension, long pos) {
        updateBuses();
        Buses of = buses.get(dimension);
        return of == null ? null : of.busAt(pos);
    }

    /** The working routers on a bus, by position. */
    public List<Long> routersOn(int dimension, Buses.Bus bus) {
        List<Long> routers = new ArrayList<>();
        for (Buses.End end : bus.ends(Buses.Kind.ROUTER)) if (working(dimension, end.pos())) routers.add(end.pos());
        return routers;
    }

    /** Whether a bus carries: it's within its length, with a working router on it. */
    public boolean carries(int dimension, Buses.Bus bus) {
        return !bus.tooLong() && !routersOn(dimension, bus).isEmpty();
    }

    /** The bus carrying a wired phone's calls: the best of those that carry, or null when none does. */
    public BusRef busOf(int phone) {
        updateBuses();
        BusRef best = null;
        for (BusRef ref : phoneBuses.getOrDefault(phone, List.of())) {
            if (!carries(ref.dimension(), ref.bus())) continue;
            if (best == null || ref.bus()
                .tier()
                .compareTo(
                    best.bus()
                        .tier())
                > 0) best = ref;
        }
        return best;
    }

    /** The KB/s a wired device's bus carries, or 0 when no bus it's on carries. */
    public int cableRate(int phone) {
        BusRef bus = busOf(phone);
        return bus == null ? 0
            : tunables.cableRate(
                bus.bus()
                    .tier());
    }

    /** The link of a wired phone's bus, to ask the calls and data how much of it they take; null for none. */
    public Object phoneLink(int phone) {
        BusRef bus = busOf(phone);
        return bus == null ? null : busLink(bus.dimension(), bus.bus());
    }

    public static BusLink busLink(int dimension, Buses.Bus bus) {
        return new BusLink(dimension, bus.id());
    }

    /**
     * The bus a gateway's calls leave by, or null when it's on none: the one that carries, else the first. A gateway
     * takes a single cable, so it's on two buses only for the moment a second joins it.
     */
    public Buses.Bus backhaulOf(int dimension, long gateway) {
        Buses.Bus first = null;
        for (Buses.Bus bus : busesAt(dimension, gateway)) {
            if (carries(dimension, bus)) return bus;
            if (first == null) first = bus;
        }
        return first;
    }

    // endregion

    // Each router's tier, as its machine says when it's placed or loaded.
    private final Map<NodeRef, CableTier> routerTiers = new HashMap<>();

    /** A router's tier, which sets how many calls it carries at once. */
    public void routerTier(World world, long pos, CableTier tier) {
        CableTier before = routerTiers.put(new NodeRef(world.provider.dimensionId, pos), tier);
        if (before != tier) version++;
    }

    private CableTier routerTier(NodeRef router) {
        return routerTiers.getOrDefault(router, CableTier.PHONE);
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

    /** Goes up on every change that can move a path: nodes, power, buses, radio links and satellites. */
    public long version() {
        updateBuses();
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

    /** The link a UHF hop between two antennas shares its rate on. */
    private record RadioLink(int dimension, NetworkModel.LinkKey key) {}

    private CallGraph build() {
        CallGraph.Builder builder = CallGraph.builder();
        for (Map.Entry<Integer, NetworkModel> entry : models.entrySet()) {
            int dimension = entry.getKey();
            NetworkModel model = entry.getValue();
            // Calls travel between antennas on UHF, once both are working, at their level's rate.
            antennas.forEachUhfLink(dimension, (a, b, snr) -> {
                if (working(dimension, a) && working(dimension, b)) {
                    builder.twoWay(
                        new CallGraph.NodeVertex(dimension, a),
                        new CallGraph.NodeVertex(dimension, b),
                        snr,
                        CallGraph.HopKind.RADIO,
                        new RadioLink(dimension, NetworkModel.LinkKey.of(a, b)),
                        tunables.radioRate(levels.levelOf(snr)));
                }
            });
            for (NetNode node : model.nodes()) {
                if (!working(dimension, node.pos())) continue;
                CallGraph.NodeVertex vertex = new CallGraph.NodeVertex(dimension, node.pos());
                if (node.kind()
                    .antenna())
                    builder.slots(
                        vertex,
                        tunables.antennaCalls(
                            node.kind()
                                .antennaTier()));
                if (node.kind() == NodeKind.EXCHANGE) builder.slots(vertex, exchangeCalls(dimension, node.pos()));
                if (node.kind() == NodeKind.ROUTER)
                    builder.slots(vertex, tunables.routerCalls(routerTier(new NodeRef(dimension, node.pos()))));
            }
        }
        // An ME Bridge joins its ME network to the line it bridges, losing nothing, and
        // carries a few calls over its one channel.
        for (Map.Entry<NodeRef, Integer> entry : meBridges.entrySet()) {
            NodeRef bridge = entry.getKey();
            if (!working(bridge.dimension(), bridge.pos())) continue;
            CallGraph.NodeVertex vertex = new CallGraph.NodeVertex(bridge.dimension(), bridge.pos());
            builder.twoWay(
                new CallGraph.MeVertex(entry.getValue(), bridge.dimension()),
                vertex,
                Double.POSITIVE_INFINITY,
                CallGraph.HopKind.ME_BRIDGE);
            builder.slots(vertex, tunables.meBridgeCalls());
        }
        // The satellites over each dimension carry a few calls each, shared by the phones under its sky and every
        // dish and receiver up to it.
        Set<Integer> skies = new HashSet<>(models.keySet());
        skies.add(Gateways.OVERWORLD);
        for (int dimension : skies) {
            builder.slots(new CallGraph.SkyVertex(dimension), gateways.skyCalls(satellitesOver(dimension)));
        }
        gateways.addTo(builder, this::satellitesOver, workingDishes(), workingReceivers(), workingRelayPairs());
        // Each bus within its length joins the working routers on it at its tier's rate, shared by every call
        // crossing it: router to router, and each phone and working gateway on it to each router. A call between two
        // phones on one bus crosses it twice, in and out of the router.
        for (Map.Entry<Integer, Buses> entry : buses.entrySet()) {
            int dimension = entry.getKey();
            for (Buses.Bus bus : entry.getValue()
                .all()) {
                if (bus.tooLong()) continue;
                List<Long> routers = routersOn(dimension, bus);
                if (routers.isEmpty()) continue;
                BusLink link = busLink(dimension, bus);
                int rate = tunables.cableRate(bus.tier());
                for (int i = 0; i < routers.size(); i++) {
                    for (int j = i + 1; j < routers.size(); j++) builder.twoWay(
                        new CallGraph.NodeVertex(dimension, routers.get(i)),
                        new CallGraph.NodeVertex(dimension, routers.get(j)),
                        Double.POSITIVE_INFINITY,
                        CallGraph.HopKind.LINE,
                        link,
                        rate);
                }
                for (Buses.End end : bus.ends()) {
                    Object vertex = switch (end.kind()) {
                        case PHONE -> end.number() < 0 ? null : new CallGraph.PhoneVertex(end.number());
                        // A gateway on two buses for a moment would join them: only the one it leaves by counts.
                        case GATEWAY -> working(dimension, end.pos()) && bus.equals(backhaulOf(dimension, end.pos()))
                            ? new CallGraph.NodeVertex(dimension, end.pos())
                            : null;
                        case ROUTER -> null;
                    };
                    if (vertex == null) continue;
                    for (long router : routers) builder.twoWay(
                        vertex,
                        new CallGraph.NodeVertex(dimension, router),
                        Double.POSITIVE_INFINITY,
                        CallGraph.HopKind.CABLE,
                        link,
                        rate);
                }
            }
        }
        return builder.build();
    }

    /** The satellites over a dimension that its sky hops can reach: none where it has no sky. */
    private int satellitesOver(int dimension) {
        return space.hasSky(dimension) ? space.count(dimension) : 0;
    }

    /** Every working Satellite Dish, for the Launch Pad's Orbit page. */
    public List<Gateways.Dish> dishes() {
        return workingDishes();
    }

    /** Every working Rocket Radio Receiver, for the Launch Pad's Orbit page. */
    public List<Gateways.Receiver> receivers() {
        return workingReceivers();
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
     * Every pair of working Portal Relays, once each: both in reach of the portal between their dimensions,
     * one end in the Overworld (9.5).
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
            NodeRef overworld = a.dimension() == Portals.OVERWORLD ? a : b;
            NodeRef far = overworld == a ? b : a;
            if (!Portals.leadsInto(portal, personalTargetsOf(overworld), far.dimension())) continue;
            pairs.add(new Gateways.RelayPair(a.dimension(), a.pos(), b.dimension(), b.pos(), pairTier(a, b)));
        }
        return pairs;
    }

    private static int compare(NodeRef a, NodeRef b) {
        int byDimension = Integer.compare(a.dimension(), b.dimension());
        return byDimension != 0 ? byDimension : Long.compare(a.pos(), b.pos());
    }

    /** How a portal trip scales coordinates into a loaded dimension (8 for the Nether), as Minecraft scales them. */
    public static double movementFactorOf(int dimension) {
        WorldServer world = DimensionManager.getWorld(dimension);
        return world == null ? 1 : world.provider.getMovementFactor();
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

    // region Sweep

    /** Queues every node of every loaded dimension for the next sweep, which drops those whose machine is gone. */
    private void queueSweep() {
        List<NodeRef> round = new ArrayList<>();
        for (Map.Entry<Integer, NetworkModel> entry : models.entrySet()) {
            for (NetNode node : entry.getValue()
                .nodes()) round.add(new NodeRef(entry.getKey(), node.pos()));
        }
        sweep.add(round);
    }

    private void sweepNode(NodeRef ref) {
        WorldServer world = DimensionManager.getWorld(ref.dimension());
        NetworkModel model = models.get(ref.dimension());
        NetNode node = model == null ? null : model.node(ref.pos());
        if (world == null || node == null || !isStale(world, node)) return;
        BeCom.LOG.info(
            "Dropping network node at {} in dimension {}: its machine is gone",
            BlockPos.toString(node.pos()),
            world.provider.dimensionId);
        removeNode(world, node.pos());
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

    // region Ticking

    /** One server tick; the node sweep gets at most {@code sweepBudgetNanos} of it. */
    public void tick(long sweepBudgetNanos) {
        tick++;
        updateBuses();
        if (tick % TICKS_PER_SECOND == 0) {
            expireOffline();
            pairRelays();
        }
        if (tick >= nextSweepTick) {
            nextSweepTick = tick + (long) SWEEP_SECONDS * TICKS_PER_SECOND;
            // A round still running from last time finishes first rather than piling up.
            if (sweep.isEmpty()) queueSweep();
        }
        sweep.run(this::sweepNode, System::nanoTime, sweepBudgetNanos);
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

    // endregion
}
