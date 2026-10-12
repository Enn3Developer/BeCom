package com.enn3developer.becom.logic.radio;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.LongSupplier;

import com.enn3developer.becom.logic.Tunables;
import com.enn3developer.becom.logic.net.NodeRef;

/**
 * Every working antenna, its survey and the links between antennas (DESIGN.md 6.3, 7), and what a device's link to
 * each one is worth. Antennas report themselves every tick while they work (D11); one not heard from for 2 seconds is
 * offline (DESIGN.md 14).
 * <p>
 * Surveys run a step at a time within a time budget per tick (DESIGN.md 6.5): a step is one bearing, or the link to one
 * other antenna on both bands. An antenna surveys when it starts working, when it moves or changes, and every refresh
 * period after that. Surveys aren't saved: a restart surveys every antenna again as it loads.
 * <p>
 * Free of Minecraft types: the server hands in each dimension's terrain and whether it has a sky. Server thread only.
 */
public final class AntennaNetwork {

    private static final long OFFLINE_AFTER_TICKS = 40;
    private static final int TICKS_PER_SECOND = 20;

    /** What the network needs to know about a dimension. */
    public interface Dimensions {

        Terrain terrain(int dimension);

        boolean noSky(int dimension);
    }

    /** Two antennas of one dimension, by position, in a fixed order. */
    public record LinkKey(long a, long b) {

        public static LinkKey of(long x, long y) {
            return x < y ? new LinkKey(x, y) : new LinkKey(y, x);
        }
    }

    /** A link between two antennas, worked out on each band (DESIGN.md 7: radio on VHF, calls on UHF). */
    public record Link(RadioModel.Losses vhf, RadioModel.Losses uhf) {}

    /** One antenna as the network knows it. */
    public static final class Antenna {

        private final NodeRef ref;
        private int tier;
        private double budget;
        private RadioHops.End end;
        private boolean locked;
        private UUID owner;
        private Survey survey;
        private long surveyedAt = Long.MIN_VALUE;
        private long seenAt = Long.MIN_VALUE;
        private boolean queued;
        private boolean wasWorking;

        Antenna(NodeRef ref) {
            this.ref = ref;
        }

        public NodeRef ref() {
            return ref;
        }

        public int tier() {
            return tier;
        }

        public double budget() {
            return budget;
        }

        public RadioHops.End end() {
            return end;
        }

        public boolean locked() {
            return locked;
        }

        public UUID owner() {
            return owner;
        }

        /** The latest finished survey, or null before the first one. */
        public Survey survey() {
            return survey;
        }
    }

    /** The working antennas of one dimension, numbered, with the VHF links radio channels travel (DESIGN.md 7). */
    public record Mesh(List<Antenna> antennas, AntennaMesh vhf) {

        public static final Mesh EMPTY = new Mesh(List.of(), new AntennaMesh(0));
    }

    /** The antenna serving a device best, and every loss on that link (DESIGN.md 6.6). */
    public record Best(Antenna antenna, RadioModel.Losses losses) {}

    private final Tunables tunables;
    private final RadioHops hops;
    private final Band vhf;
    private final Band uhf;
    private final Dimensions dimensions;
    private final BiPredicate<UUID, UUID> sameTeam;
    private final long surveyTicks;
    private final Map<NodeRef, Antenna> antennas = new HashMap<>();
    private final Map<Integer, Map<LinkKey, Link>> links = new HashMap<>();
    private final Deque<Job> jobs = new ArrayDeque<>();
    private final Map<Integer, Mesh> meshes = new HashMap<>();
    private long meshesVersion = -1;
    private long version;
    private long tick;

    /** @param sameTeam whether two players share a GT team, for team-locked antennas (DESIGN.md 12.2) */
    public AntennaNetwork(Tunables tunables, Dimensions dimensions, BiPredicate<UUID, UUID> sameTeam) {
        this.tunables = tunables;
        this.hops = new RadioHops(tunables.radioModel(), tunables.surveyStep());
        this.vhf = tunables.vhf();
        this.uhf = tunables.uhf();
        this.dimensions = dimensions;
        this.sameTeam = sameTeam;
        this.surveyTicks = (long) tunables.surveySeconds() * TICKS_PER_SECOND;
    }

    // region Antennas

    /**
     * An antenna is formed and powered this tick. {@code end} is its head: where it transmits from, how far below the
     * surface that is, and the top of its casings.
     */
    public void seen(NodeRef ref, int tier, RadioHops.End end, boolean locked, UUID owner) {
        Antenna antenna = antennas.computeIfAbsent(ref, Antenna::new);
        boolean changed = antenna.end == null || !antenna.end.equals(end) || antenna.tier != tier;
        if (antenna.locked != locked || !working(antenna)) version++;
        antenna.locked = locked;
        antenna.owner = owner;
        antenna.seenAt = tick;
        antenna.wasWorking = true;
        if (changed) {
            // A taller mast, a new tier or a rebuilt antenna: what it surveyed no longer holds, and a survey under way
            // is for the old one (it notices and stops).
            antenna.tier = tier;
            antenna.budget = tunables.antennaBudget(tier);
            antenna.end = end;
            antenna.survey = null;
            antenna.surveyedAt = Long.MIN_VALUE;
            antenna.queued = false;
            dropLinks(ref);
            queue(antenna);
        }
    }

    /** The antenna was broken or lost its structure. */
    public void gone(NodeRef ref) {
        if (antennas.remove(ref) != null) {
            dropLinks(ref);
            version++;
        }
    }

    public boolean working(Antenna antenna) {
        return antenna.seenAt != Long.MIN_VALUE && tick - antenna.seenAt <= OFFLINE_AFTER_TICKS;
    }

    public Antenna antenna(NodeRef ref) {
        return antennas.get(ref);
    }

    public int size() {
        return antennas.size();
    }

    /** Goes up whenever a link, a lock or which antennas work changes. */
    public long version() {
        return version;
    }

    /** Ticks since the network started. */
    public long currentTick() {
        return tick;
    }

    private void dropLinks(NodeRef ref) {
        Map<LinkKey, Link> dimension = links.get(ref.dimension());
        if (dimension == null) return;
        if (dimension.keySet()
            .removeIf(k -> k.a() == ref.pos() || k.b() == ref.pos())) version++;
    }

    // endregion

    // region Ticking and surveys

    /** One server tick: notices antennas going offline, queues surveys that are due, and runs survey steps. */
    public void tick(long budgetNanos, LongSupplier clock) {
        tick++;
        if (tick % TICKS_PER_SECOND == 0) {
            Iterator<Antenna> it = antennas.values()
                .iterator();
            while (it.hasNext()) {
                Antenna antenna = it.next();
                boolean working = working(antenna);
                if (antenna.wasWorking && !working) version++;
                antenna.wasWorking = working;
                if (!working && tick - antenna.seenAt > Math.max(surveyTicks, OFFLINE_AFTER_TICKS)) {
                    // Gone for longer than a survey lasts: unloaded or broken while unloaded. Forget it.
                    it.remove();
                    dropLinks(antenna.ref);
                } else if (working && !antenna.queued && tick - antenna.surveyedAt >= surveyTicks) {
                    queue(antenna);
                }
            }
        }
        runSurveys(budgetNanos, clock);
    }

    /** Runs survey steps until the queue is empty or the budget is spent, always at least one. */
    public void runSurveys(long budgetNanos, LongSupplier clock) {
        long start = clock.getAsLong();
        while (!jobs.isEmpty()) {
            Job job = jobs.peek();
            if (job.step()) jobs.poll();
            if (clock.getAsLong() - start >= budgetNanos) break;
        }
    }

    /** Surveys still queued or running. */
    public int surveysQueued() {
        return jobs.size();
    }

    private void queue(Antenna antenna) {
        if (antenna.queued) return;
        antenna.queued = true;
        jobs.add(new Job(antenna));
    }

    /** One antenna's survey, taken a step at a time. */
    private final class Job {

        private final Antenna target;
        private final RadioHops.End from;
        private final Survey.Builder builder;
        private final Terrain terrain;
        private final boolean noSky;
        private List<Antenna> others;
        private int next;
        private final Map<LinkKey, Link> found = new HashMap<>();

        Job(Antenna target) {
            this.target = target;
            this.from = target.end;
            int dimension = target.ref.dimension();
            this.terrain = dimensions.terrain(dimension);
            this.noSky = dimensions.noSky(dimension);
            this.builder = new Survey.Builder(
                from.x(),
                from.z(),
                tunables.surveyBearings(),
                tunables.surveyStep(),
                Survey.stepsFor(tunables.rangeToHold(target.budget), tunables.surveyStep()));
        }

        /** Does one step; true once the survey is finished or no longer wanted. */
        boolean step() {
            if (antennas.get(target.ref) != target || !target.end.equals(from)) {
                // Broken or changed since it was queued; a changed antenna has queued a fresh survey.
                return true;
            }
            if (!builder.done()) {
                builder.next(terrain);
                return false;
            }
            if (others == null) {
                others = new ArrayList<>();
                for (Antenna other : antennas.values()) {
                    if (other != target && other.ref.dimension() == target.ref.dimension() && other.end != null) {
                        others.add(other);
                    }
                }
            }
            if (next < others.size()) {
                link(others.get(next++));
                return false;
            }
            finish();
            return true;
        }

        private void link(Antenna other) {
            if (antennas.get(other.ref) != other || other.end == null) return;
            double budget = Math.min(target.budget, other.budget);
            if (from.distanceTo(other.end) > tunables.rangeToLow(budget)) return;
            RadioModel.Losses onVhf = hops.direct(terrain, from, other.end, budget, vhf, 0, noSky);
            RadioModel.Losses onUhf = hops.direct(terrain, from, other.end, budget, uhf, 0, noSky);
            double low = tunables.levelLow();
            // Only links at Low or better exist (DESIGN.md 7).
            if (onVhf.snr() < low && onUhf.snr() < low) return;
            found.put(LinkKey.of(target.ref.pos(), other.ref.pos()), new Link(onVhf, onUhf));
        }

        private void finish() {
            target.survey = builder.build();
            target.surveyedAt = tick;
            target.queued = false;
            Map<LinkKey, Link> dimension = links.computeIfAbsent(target.ref.dimension(), d -> new HashMap<>());
            long pos = target.ref.pos();
            dimension.keySet()
                .removeIf(k -> k.a() == pos || k.b() == pos);
            dimension.putAll(found);
            version++;
        }
    }

    // endregion

    // region Links, meshes and access

    /** Every link of a dimension found by its antennas' surveys, working or not. */
    public Map<LinkKey, Link> links(int dimension) {
        Map<LinkKey, Link> found = links.get(dimension);
        return found == null ? Collections.emptyMap() : Collections.unmodifiableMap(found);
    }

    /** The links calls can take: both antennas working and the UHF link at Low or better. */
    public void forEachUhfLink(int dimension, LinkConsumer consumer) {
        Map<LinkKey, Link> found = links.get(dimension);
        if (found == null) return;
        double low = tunables.levelLow();
        for (Map.Entry<LinkKey, Link> entry : found.entrySet()) {
            double snr = entry.getValue()
                .uhf()
                .snr();
            if (snr < low) continue;
            Antenna a = antennas.get(
                new NodeRef(
                    dimension,
                    entry.getKey()
                        .a()));
            Antenna b = antennas.get(
                new NodeRef(
                    dimension,
                    entry.getKey()
                        .b()));
            if (a != null && b != null && working(a) && working(b)) {
                consumer.accept(
                    entry.getKey()
                        .a(),
                    entry.getKey()
                        .b(),
                    snr);
            }
        }
    }

    @FunctionalInterface
    public interface LinkConsumer {

        void accept(long a, long b, double snr);
    }

    /** The working antennas of a dimension and their VHF links, rebuilt when anything they depend on changed. */
    public Mesh mesh(int dimension) {
        if (meshesVersion != version) {
            meshes.clear();
            meshesVersion = version;
        }
        return meshes.computeIfAbsent(dimension, this::buildMesh);
    }

    private Mesh buildMesh(int dimension) {
        List<Antenna> working = new ArrayList<>();
        for (Antenna antenna : antennas.values()) {
            if (antenna.ref.dimension() == dimension && working(antenna) && antenna.end != null) working.add(antenna);
        }
        if (working.isEmpty()) return Mesh.EMPTY;
        working.sort(Comparator.comparingLong(a -> a.ref.pos()));
        AntennaMesh mesh = new AntennaMesh(working.size());
        Map<LinkKey, Link> found = links.getOrDefault(dimension, Collections.emptyMap());
        double low = tunables.levelLow();
        for (int i = 0; i < working.size(); i++) {
            for (int j = i + 1; j < working.size(); j++) {
                Link link = found.get(LinkKey.of(working.get(i).ref.pos(), working.get(j).ref.pos()));
                if (link != null && link.vhf()
                    .snr() >= low) {
                    mesh.link(
                        i,
                        j,
                        link.vhf()
                            .snr());
                }
            }
        }
        return new Mesh(Collections.unmodifiableList(working), mesh);
    }

    /**
     * A device's VHF links to each antenna of {@code mesh}, in its order: negative infinity for an antenna out of
     * range,
     * not surveyed yet, or locked to a team {@code user} isn't in (DESIGN.md 12.2).
     */
    public double[] access(Mesh mesh, int dimension, RadioHops.End device, UUID user) {
        double[] access = new double[mesh.antennas()
            .size()];
        boolean noSky = dimensions.noSky(dimension);
        for (int i = 0; i < access.length; i++) {
            RadioModel.Losses losses = losses(
                mesh.antennas()
                    .get(i),
                device,
                user,
                vhf,
                noSky);
            access[i] = losses == null ? Double.NEGATIVE_INFINITY : losses.snr();
        }
        return access;
    }

    /** The antenna that serves a device best on {@code band}, or null when none reaches it. */
    public Best best(int dimension, RadioHops.End device, UUID user, Band band) {
        Best best = null;
        boolean noSky = dimensions.noSky(dimension);
        for (Antenna antenna : mesh(dimension).antennas()) {
            RadioModel.Losses losses = losses(antenna, device, user, band, noSky);
            if (losses != null && (best == null || losses.snr() > best.losses()
                .snr())) best = new Best(antenna, losses);
        }
        return best;
    }

    private RadioModel.Losses losses(Antenna antenna, RadioHops.End device, UUID user, Band band, boolean noSky) {
        if (antenna.survey == null) return null;
        if (antenna.locked && !sameTeam.test(user, antenna.owner)) return null;
        if (antenna.end.distanceTo(device) > tunables.rangeToHold(antenna.budget)) return null;
        return hops.access(antenna.survey, antenna.end, antenna.budget, device, band, 0, noSky);
    }

    public Band vhf() {
        return vhf;
    }

    public Band uhf() {
        return uhf;
    }

    // endregion
}
