package com.enn3developer.becom.server;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.S29PacketSoundEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.FakePlayer;

import com.enn3developer.becom.BeCom;
import com.enn3developer.becom.item.Carried;
import com.enn3developer.becom.item.ItemSignalAnalyzer;
import com.enn3developer.becom.item.ItemWalkieTalkie;
import com.enn3developer.becom.logic.DropoutModel;
import com.enn3developer.becom.logic.LevelScale;
import com.enn3developer.becom.logic.Positions;
import com.enn3developer.becom.logic.SignalLevel;
import com.enn3developer.becom.logic.Slots;
import com.enn3developer.becom.logic.SpeakerFade;
import com.enn3developer.becom.logic.Tunables;
import com.enn3developer.becom.logic.net.BlockPos;
import com.enn3developer.becom.logic.net.NodeRef;
import com.enn3developer.becom.logic.phone.PhoneKind;
import com.enn3developer.becom.logic.radio.AntennaMesh;
import com.enn3developer.becom.logic.radio.AntennaNetwork;
import com.enn3developer.becom.logic.radio.Band;
import com.enn3developer.becom.logic.radio.RadioHops;
import com.enn3developer.becom.logic.radio.RadioLinkLevel;
import com.enn3developer.becom.logic.radio.RadioModel;
import com.enn3developer.becom.packet.AnalyzerPacket;
import com.enn3developer.becom.packet.BeComPackets;
import com.enn3developer.becom.packet.LevelPacket;
import com.enn3developer.becom.quest.BeComQuests;
import com.enn3developer.becom.quest.QuestTask;
import com.enn3developer.becom.voice.Nearby;
import com.enn3developer.becom.voice.RadioGroup;
import com.enn3developer.becom.voice.RadioRouting;
import com.enn3developer.becom.voice.RadioSnapshot;
import com.enn3developer.gtnhvoice.api.server.GtnhVoiceApi;
import com.enn3developer.gtnhvoice.api.server.PlayerSnapshot;

/**
 * Radio channels in the world (DESIGN.md 8.1, 13.2, 13.3): who carries a working walkie or stands at a Transceiver,
 * their memberships of the sixteen {@code becom:radio.N} groups, and the routing snapshot for whoever holds
 * push-to-talk.
 * Server thread only.
 * <p>
 * Each player's radio and each Transceiver's links to the antennas are refreshed once a second, spread over the twenty
 * ticks (DESIGN.md 6.5). Direct links are worked out only for speakers keyed right now: a speaker shows up in the
 * routing state with their first frame, gets a transmission on the next tick, and is worked out again once a second
 * after that, in their own tick of the second, so the radios keyed at once are spread over it too. Levels go to
 * clients only when they change (DESIGN.md 13.5).
 */
public final class RadioService {

    private static final int TICKS_PER_SECOND = 20;
    // A speaker whose last radio frame is older than this has let go of the key: voice frames are 20 ms apart.
    private static final long RELEASED_AFTER_NANOS = 250_000_000L;
    // A Transceiver not heard from for 2 seconds is offline (DESIGN.md 14).
    private static final long OFFLINE_AFTER_TICKS = 40;
    // A listener keeps the level of someone who stopped talking for this long, so their next transmission isn't
    // heard clean for the moment before its level arrives.
    private static final long FORGET_LEVEL_TICKS = 60L * TICKS_PER_SECOND;
    // Players' eyes sit this far above their feet, which is what posY is on the server.
    private static final double EYE_HEIGHT = 1.62;
    private static final String SQUELCH = BeCom.MODID + ":radio.squelch";
    private static final float SQUELCH_VOLUME = 0.5f;

    private final MinecraftServer server;
    private final Tunables tunables;
    private final AntennaNetwork antennas;
    private final HeightCaches heights;
    private final SpaceService space;
    private final RadioHops hops;
    private final Band vhf;
    private final LevelScale levels;
    private final DropoutModel dropouts;
    private final long dropGraceMillis;
    private final RadioRouting routing;
    private final RadioGroup[] groups = new RadioGroup[RadioGroup.CHANNELS + 1];

    /** A Transceiver as it last reported, and its links to the antennas. */
    private static final class Transceiver {

        NodeRef ref;
        int channel;
        boolean loudspeaker;
        RadioHops.End end;
        boolean rains;
        double sky = Double.NEGATIVE_INFINITY;
        UUID owner;
        long seenAt;
        double[] access;
        AntennaNetwork.Mesh accessMesh;
    }

    /** A player's radio as of their last refresh: their walkie's channel, or the Transceiver they stand at. */
    private static final class Radio {

        UUID id;
        int dimension;
        RadioHops.End end;
        boolean rains;
        double sky = Double.NEGATIVE_INFINITY;
        int walkieChannel;
        NodeRef transceiver;
        double[] access;
        AntennaNetwork.Mesh accessMesh;
    }

    /**
     * What a speaker transmits from: their walkie, or the Transceiver they stand at (DESIGN.md 8.1); {@code sky} is its
     * hop up to the satellites (9.1).
     */
    private record Tx(int channel, int dimension, RadioHops.End end, double budget, double[] access, NodeRef from,
        double sky) {

        /**
         * Whether this is the same radio in the same place on the same channel as {@code other}. Its access to the
         * antennas and its sky hop aren't compared: they change with the network and the weather, which each keyed
         * radio takes in its own tick of the second, so one antenna's change doesn't work out every radio at once.
         */
        boolean sameSource(Tx other) {
            return channel == other.channel && dimension == other.dimension
                && end.equals(other.end)
                && Double.compare(budget, other.budget) == 0
                && Objects.equals(from, other.from);
        }
    }

    private record Pair(UUID speaker, Object device) {}

    /** A keyed radio's transmission as last worked out, and what it transmitted from then. */
    private record Links(Tx tx, RadioSnapshot.Transmission transmission) {}

    /** A pair's link level, and the tick it was last evaluated. */
    private static final class PairLevel {

        final RadioLinkLevel link;
        long tick;

        PairLevel(RadioLinkLevel link) {
            this.link = link;
        }
    }

    private record Heard(UUID listener, UUID speaker) {}

    private record Sent(int channel, SignalLevel level, long tick) {}

    private final Map<NodeRef, Transceiver> transceivers = new HashMap<>();
    private final Map<UUID, Radio> radios = new HashMap<>();
    private final Map<UUID, Links> evaluated = new HashMap<>();
    private final Map<UUID, Integer> joined = new HashMap<>();
    private final Set<UUID> active = new HashSet<>();
    private final Map<Pair, PairLevel> pairLevels = new HashMap<>();
    private final Map<Heard, Sent> sent = new HashMap<>();
    private RadioSnapshot published = RadioSnapshot.EMPTY;
    // A Transceiver broken since the last tick, which, like one gone offline, nobody hears through from the next.
    private boolean broken;
    private long tick;

    public RadioService(MinecraftServer server, Tunables tunables, AntennaNetwork antennas, HeightCaches heights,
        SpaceService space) {
        this.server = server;
        this.tunables = tunables;
        this.antennas = antennas;
        this.heights = heights;
        this.space = space;
        this.hops = new RadioHops(tunables.radioModel(), tunables.surveyStep());
        this.vhf = tunables.vhf();
        this.levels = tunables.levelScale();
        this.dropouts = tunables.dropoutModel(Tunables.FRAME_MILLIS);
        this.dropGraceMillis = (long) (tunables.dropAfterSeconds() * 1000);
        this.routing = new RadioRouting(
            tunables.directHearingRange(),
            GtnhVoiceApi::proximityRange,
            tunables.walkieSpeakerRange(),
            tunables.loudspeakerRange(),
            tunables.captureHoldMillis() * 1_000_000L,
            tunables.captureMargin(),
            () -> java.util.concurrent.ThreadLocalRandom.current()
                .nextDouble(),
            System::nanoTime);
        for (int channel = 1; channel <= RadioGroup.CHANNELS; channel++) {
            groups[channel] = new RadioGroup(channel, routing);
        }
    }

    // region Lifecycle

    /**
     * Registers the sixteen channels with GTNH Voice, which forgets them whenever its server stops (DESIGN.md 13.1).
     */
    public void start() {
        for (int channel = 1; channel <= RadioGroup.CHANNELS; channel++) {
            GtnhVoiceApi.groupManager()
                .registerGroup(groups[channel]);
        }
    }

    public void stop() {
        routing.clear();
        transceivers.clear();
        radios.clear();
        joined.clear();
        active.clear();
        pairLevels.clear();
        sent.clear();
        evaluated.clear();
        published = RadioSnapshot.EMPTY;
        broken = false;
    }

    public RadioGroup group(int channel) {
        return groups[channel];
    }

    public RadioRouting routing() {
        return routing;
    }

    /** sessionStarted: GTNH Voice wiped the player's memberships, so the next second joins them again. */
    public void sessionStarted(UUID player) {
        joined.remove(player);
    }

    public void logout(UUID player) {
        radios.remove(player);
        joined.remove(player);
        active.remove(player);
        sent.keySet()
            .removeIf(h -> h.listener.equals(player) || h.speaker.equals(player));
        pairLevels.keySet()
            .removeIf(p -> p.speaker.equals(player) || player.equals(p.device));
    }

    // endregion

    // region Transceivers

    /** A Transceiver is powered this tick. {@code end} is its block's top, its depth and its base. */
    public void transceiverTick(NodeRef ref, int channel, boolean loudspeaker, RadioHops.End end, UUID owner) {
        Transceiver t = transceivers.computeIfAbsent(ref, r -> new Transceiver());
        if (t.ref == null || t.channel != channel || t.loudspeaker != loudspeaker || !end.equals(t.end)) {
            t.ref = ref;
            t.channel = channel;
            t.loudspeaker = loudspeaker;
            if (!end.equals(t.end)) {
                t.accessMesh = null;
                WorldServer world = DimensionManager.getWorld(ref.dimension());
                t.rains = world != null
                    && BeComServer.rains(world, (int) Math.floor(end.x()), (int) Math.floor(end.z()));
                t.end = end;
                t.sky = skyHop(ref.dimension(), end, t.rains);
            }
        }
        t.owner = owner;
        t.seenAt = tick;
    }

    public void transceiverGone(NodeRef ref) {
        if (transceivers.remove(ref) != null) broken = true;
    }

    private boolean working(Transceiver t) {
        return tick - t.seenAt <= OFFLINE_AFTER_TICKS;
    }

    // endregion

    // region Ticking

    public void tick() {
        tick++;
        long now = System.nanoTime();
        // One pass over the quest book for everyone this tick's contacts name: each completion walks every quest.
        Set<UUID> contacted = new HashSet<>();
        RadioRouting.Contact contact;
        while ((contact = routing.pollContact()) != null) {
            contacted.add(contact.speaker());
            contacted.add(contact.listener());
        }
        if (!contacted.isEmpty()) BeComQuests.complete(QuestTask.RADIO_CONTACT, contacted);
        Set<UUID> fresh = new HashSet<>();
        keys(now, fresh);
        boolean everyone = broken;
        broken = false;
        for (EntityPlayerMP player : players()) {
            if (Slots.due(player.getUniqueID(), tick)) refresh(player);
        }
        Iterator<Transceiver> it = transceivers.values()
            .iterator();
        while (it.hasNext()) {
            Transceiver t = it.next();
            if (!working(t)) {
                it.remove();
                // Nobody hears through a device that's gone, so every radio's links are worked out again now.
                everyone = true;
            } else if (Slots.due(t.ref, tick)) {
                refreshAccess(t);
                t.sky = skyHop(t.ref.dimension(), t.end, t.rains);
            }
        }
        if (tick % TICKS_PER_SECOND == 0) {
            memberships();
            drainWalkies(players());
            forgetOld(now);
        }
        if (tick % ((long) tunables.analyzerRefreshSeconds() * TICKS_PER_SECOND) == 0) analyzers();
        // Each keyed radio is worked out once a second, in its own tick of the second, the one its holder is refreshed
        // in (DESIGN.md 6.4, 6.5); one that pressed push-to-talk, moved or changed what it transmits from, at once.
        for (UUID speaker : active) {
            if (Slots.due(speaker, tick)) fresh.add(speaker);
        }
        publish(everyone ? null : fresh);
    }

    /** Notices speakers pressing and releasing push-to-talk, and adds those who pressed to {@code pressed}. */
    private void keys(long now, Set<UUID> pressed) {
        Map<UUID, RadioRouting.Keyed> keyed = routing.keyedSpeakers();
        for (Map.Entry<UUID, RadioRouting.Keyed> entry : keyed.entrySet()) {
            if (now - entry.getValue()
                .nanos() <= RELEASED_AFTER_NANOS && active.add(entry.getKey())) pressed.add(entry.getKey());
        }
        Iterator<UUID> it = active.iterator();
        while (it.hasNext()) {
            UUID speaker = it.next();
            RadioRouting.Keyed k = keyed.get(speaker);
            if (k == null || now - k.nanos() > RELEASED_AFTER_NANOS) {
                squelch(speaker);
                it.remove();
            }
        }
    }

    private void forgetOld(long now) {
        routing.sweep(now, RELEASED_AFTER_NANOS * 4);
        pairLevels.values()
            .removeIf(p -> tick - p.tick > FORGET_LEVEL_TICKS);
        Iterator<Map.Entry<Heard, Sent>> it = sent.entrySet()
            .iterator();
        while (it.hasNext()) {
            Map.Entry<Heard, Sent> entry = it.next();
            if (active.contains(entry.getKey().speaker) || tick - entry.getValue().tick <= FORGET_LEVEL_TICKS) continue;
            EntityPlayerMP listener = player(entry.getKey().listener);
            if (listener != null) {
                BeComPackets.send(
                    new LevelPacket(
                        entry.getKey().speaker,
                        RadioGroup.nameOf(entry.getValue().channel),
                        SignalLevel.NONE),
                    listener);
            }
            it.remove();
        }
    }

    // endregion

    // region Radios

    /** Looks at a player's inventory and surroundings again (DESIGN.md 6.5: once a second per player). */
    private void refresh(EntityPlayerMP player) {
        UUID id = player.getUniqueID();
        Carried.Slot slot = ItemWalkieTalkie.active(player);
        int walkieChannel = slot == null ? 0 : ItemWalkieTalkie.channel(slot.stack());
        NodeRef at = transceiverAt(player);
        // A player whose position isn't a number has none, and a radio with no position has no links.
        RadioHops.End end = endOf(player);
        if ((walkieChannel == 0 && at == null) || end == null) {
            radios.remove(id);
            return;
        }
        Radio radio = radios.computeIfAbsent(id, k -> new Radio());
        radio.id = id;
        radio.dimension = player.dimension;
        radio.end = end;
        radio.rains = BeComServer.rains(player.worldObj, (int) Math.floor(player.posX), (int) Math.floor(player.posZ));
        radio.sky = skyHop(radio.dimension, radio.end, radio.rains);
        radio.walkieChannel = walkieChannel;
        radio.transceiver = at;
        radio.access = null;
        radio.accessMesh = null;
        if (walkieChannel > 0) accessOf(radio);
    }

    /** The nearest working Transceiver within talk range of {@code player} (DESIGN.md 8.1), or null. */
    private NodeRef transceiverAt(EntityPlayer player) {
        NodeRef best = null;
        double bestSq = tunables.talkRange() * tunables.talkRange();
        for (Transceiver t : transceivers.values()) {
            if (t.ref.dimension() != player.dimension || !working(t)) continue;
            double dx = BlockPos.x(t.ref.pos()) + 0.5 - player.posX;
            double dy = BlockPos.y(t.ref.pos()) + 0.5 - (player.posY + 1);
            double dz = BlockPos.z(t.ref.pos()) + 0.5 - player.posZ;
            double sq = dx * dx + dy * dy + dz * dz;
            if (sq <= bestSq) {
                bestSq = sq;
                best = t.ref;
            }
        }
        return best;
    }

    private double[] accessOf(Radio radio) {
        AntennaNetwork.Mesh mesh = antennas.mesh(radio.dimension);
        if (radio.accessMesh != mesh) {
            radio.access = antennas.access(mesh, radio.dimension, radio.end, radio.id);
            radio.accessMesh = mesh;
        }
        return radio.access;
    }

    private double[] refreshAccess(Transceiver t) {
        AntennaNetwork.Mesh mesh = antennas.mesh(t.ref.dimension());
        if (t.accessMesh != mesh) {
            t.access = antennas.access(mesh, t.ref.dimension(), t.end, t.owner);
            t.accessMesh = mesh;
        }
        return t.access;
    }

    /** What a radio transmits from, or null when it can't (DESIGN.md 8.1: a Transceiver at hand wins). */
    private Tx txOf(Radio radio) {
        Transceiver at = radio.transceiver == null ? null : transceivers.get(radio.transceiver);
        if (at != null && working(at)) {
            return new Tx(
                at.channel,
                at.ref.dimension(),
                at.end,
                tunables.transceiverBudget(),
                refreshAccess(at),
                at.ref,
                at.sky);
        }
        if (radio.walkieChannel > 0) {
            return new Tx(
                radio.walkieChannel,
                radio.dimension,
                radio.end,
                tunables.walkieBudget(),
                accessOf(radio),
                null,
                radio.sky);
        }
        return null;
    }

    /** The channel a player transmits on, or 0 (DESIGN.md 13.2: that's the group they're in). */
    public int channelOf(UUID player) {
        Radio radio = radios.get(player);
        Tx tx = radio == null ? null : txOf(radio);
        return tx == null ? 0 : tx.channel;
    }

    private void memberships() {
        Set<UUID> online = new HashSet<>();
        for (EntityPlayerMP player : players()) {
            UUID id = player.getUniqueID();
            online.add(id);
            int channel = channelOf(id);
            Integer was = joined.get(id);
            if (was != null && was == channel) continue;
            if (was != null && was > 0) GtnhVoiceApi.groupManager()
                .leave(id, groups[was]);
            if (channel > 0) GtnhVoiceApi.groupManager()
                .join(id, groups[channel]);
            joined.put(id, channel);
        }
        joined.keySet()
            .retainAll(online);
    }

    // endregion

    // region Snapshot

    /**
     * Builds the routing snapshot for everyone keyed, and sends listeners the levels that changed. A keyed radio's
     * links are worked out again when it keys, moves or changes what it transmits from, and once a second (DESIGN.md
     * 6.4); otherwise its last transmission stands as it was, so neither a press nor a radio's tick of the second works
     * out every other keyed radio's links again. A change to the antennas or the sky reaches each radio in its own
     * tick.
     * Nothing is published when nothing changed.
     *
     * @param fresh the radios to work out again even if nothing changed, or null for all of them
     */
    private void publish(Set<UUID> fresh) {
        List<UUID> speakers = new ArrayList<>(active);
        speakers.sort(Comparator.naturalOrder());
        List<Radio> receivers = null;
        List<Transceiver> loudspeakers = null;
        List<UUID> worked = new ArrayList<>();
        Map<UUID, RadioSnapshot.Transmission> transmissions = new HashMap<>();
        for (UUID speaker : speakers) {
            Radio radio = radios.get(speaker);
            Tx tx = radio == null ? null : txOf(radio);
            if (tx == null) continue;
            Links links = evaluated.get(speaker);
            if (fresh == null || fresh.contains(speaker)
                || links == null
                || !links.tx()
                    .sameSource(tx)) {
                if (receivers == null) {
                    receivers = new ArrayList<>(radios.values());
                    receivers.sort(Comparator.comparing(r -> r.id));
                    loudspeakers = new ArrayList<>(transceivers.values());
                    loudspeakers.sort(Comparator.comparing(t -> t.ref.pos()));
                }
                links = links(speaker, radio, tx, receivers, loudspeakers);
                evaluated.put(speaker, links);
                worked.add(speaker);
            }
            transmissions.put(speaker, links.transmission());
        }
        evaluated.keySet()
            .retainAll(active);
        if (worked.isEmpty() && published(transmissions)) return;
        RadioSnapshot.Builder builder = RadioSnapshot.builder();
        transmissions.forEach(builder::transmission);
        published = builder.build();
        routing.publish(published);
        sendLevels(worked);
    }

    /** Whether the published snapshot holds exactly these transmissions, so publishing them again changes nothing. */
    private boolean published(Map<UUID, RadioSnapshot.Transmission> transmissions) {
        if (transmissions.size() != published.speakers()) return false;
        for (Map.Entry<UUID, RadioSnapshot.Transmission> entry : transmissions.entrySet()) {
            if (published.transmission(entry.getKey()) != entry.getValue()) return false;
        }
        return true;
    }

    /**
     * A keyed radio's links to every receiver on its channel that hears it at Low or better, with each pair's level.
     */
    private Links links(UUID speaker, Radio radio, Tx tx, List<Radio> receivers, List<Transceiver> loudspeakers) {
        List<RadioSnapshot.Link> links = new ArrayList<>();
        AntennaNetwork.Mesh mesh = antennas.mesh(tx.dimension);
        double[] reach = tx.access != null && tx.access.length == mesh.antennas()
            .size() ? mesh.vhf()
                .reach(tx.access) : null;
        for (Radio other : receivers) {
            if (other == radio || other.walkieChannel != tx.channel || other.dimension != tx.dimension) continue;
            double snr = snr(tx, other.end, accessOf(other), reach, other.sky);
            SignalLevel level = level(new Pair(speaker, other.id), snr);
            if (level.atLeast(SignalLevel.LOW)) links.add(link(other.id, null, snr));
        }
        for (Transceiver t : loudspeakers) {
            if (!working(t) || t.channel != tx.channel || t.ref.dimension() != tx.dimension || t.ref.equals(tx.from))
                continue;
            double snr = snr(tx, t.end, refreshAccess(t), reach, t.sky);
            SignalLevel level = level(new Pair(speaker, t.ref), snr);
            if (level.atLeast(SignalLevel.LOW)) {
                links.add(
                    link(
                        null,
                        new RadioSnapshot.Transceiver(
                            t.ref.dimension(),
                            t.ref.pos(),
                            t.end.x(),
                            t.end.y() - 0.5,
                            t.end.z(),
                            t.loudspeaker),
                        snr));
            }
        }
        return new Links(tx, RadioSnapshot.transmission(tx.channel, links));
    }

    private RadioSnapshot.Link link(UUID holder, RadioSnapshot.Transceiver transceiver, double snr) {
        double loss = dropouts.lossRate(snr);
        return new RadioSnapshot.Link(
            holder,
            transceiver,
            snr,
            dropouts.enterBadChance(loss),
            dropouts.leaveBadChance());
    }

    /**
     * The best of the direct link, the path through the antennas (DESIGN.md 8.1) and the path through the satellites,
     * up from one radio and down to the other (9.1). A direct link uses the transmitter's budget (6.1); through
     * antennas, every hop uses the antennas' budgets.
     */
    private double snr(Tx tx, RadioHops.End to, double[] access, double[] reach, double sky) {
        double direct = Double.NEGATIVE_INFINITY;
        if (tx.end.distanceTo(to) <= tunables.rangeToHold(tx.budget)) {
            direct = hops.direct(heights.terrain(tx.dimension), tx.end, to, tx.budget, vhf, 0, noSky(tx.dimension))
                .snr();
        }
        double via = reach != null && access != null && access.length == reach.length
            ? AntennaMesh.through(reach, access)
            : Double.NEGATIVE_INFINITY;
        return Math.max(Math.max(direct, via), Math.min(tx.sky, sky));
    }

    /**
     * A radio's hop up to the satellites over its dimension on VHF (DESIGN.md 9.1): their strength less its cover,
     * and the rain where it stands (D26). Negative infinity without enough satellites.
     */
    private double skyHop(int dimension, RadioHops.End end, boolean rains) {
        return space.hop(dimension, end.depth(), vhf, rains ? space.weather(dimension) : 0);
    }

    /**
     * The pair's level after this evaluation: hysteresis, the radios' Medium cap (D10), and the drop after the grace
     * below Low (DESIGN.md 4.2).
     */
    private SignalLevel level(Pair pair, double snr) {
        PairLevel pairLevel = pairLevels
            .computeIfAbsent(pair, p -> new PairLevel(new RadioLinkLevel(levels, SignalLevel.MEDIUM, dropGraceMillis)));
        pairLevel.tick = tick;
        return pairLevel.link.update(snr, System.nanoTime() / 1_000_000L);
    }

    /**
     * Tells each listener the level they hear each speaker at, in the order {@code route()} sends: their own walkie,
     * then the nearest walkie speaker in the snapshot's order, then a loudspeaker (DESIGN.md 13.3, 13.5).
     */
    private void sendLevels(List<UUID> speakers) {
        List<EntityPlayerMP> online = players();
        Map<UUID, PlayerSnapshot> positions = new HashMap<>();
        for (EntityPlayerMP player : online) {
            // A listener whose position isn't a number has none, so no device carries anything to them.
            if (!Positions.finite(player.posX, player.posY, player.posZ)) continue;
            positions.put(
                player.getUniqueID(),
                new PlayerSnapshot(
                    player.getUniqueID(),
                    player.getCommandSenderName(),
                    player.posX,
                    player.posY,
                    player.posZ,
                    player.dimension));
        }
        Nearby nearby = new Nearby(positions.values());
        for (UUID speaker : speakers) {
            RadioSnapshot.Transmission tx = published.transmission(speaker);
            if (tx == null) continue;
            String group = RadioGroup.nameOf(tx.channel());
            Map<UUID, List<RadioRouting.Carrier>> carriers = routing
                .carriers(speaker, tx, positions, nearby, link -> levelOf(speaker, levelKey(link)));
            for (EntityPlayerMP listener : online) {
                UUID id = listener.getUniqueID();
                if (id.equals(speaker)) continue;
                SignalLevel level = routing.heardThrough(speaker, tx.channel(), carriers.getOrDefault(id, List.of()));
                Heard heard = new Heard(id, speaker);
                Sent before = sent.get(heard);
                if (level == SignalLevel.NONE && before == null) continue;
                if (before == null || before.level != level || before.channel != tx.channel()) {
                    BeComPackets.send(new LevelPacket(speaker, group, level), listener);
                }
                if (level == SignalLevel.NONE) sent.remove(heard);
                else sent.put(heard, new Sent(tx.channel(), level, tick));
            }
        }
    }

    /** What a link's level is kept under: a walkie's holder, or a Transceiver's place. */
    private static Object levelKey(RadioSnapshot.Link link) {
        RadioSnapshot.Transceiver t = link.transceiver();
        return link.holder() != null ? link.holder() : new NodeRef(t.dimension(), t.pos());
    }

    private SignalLevel levelOf(UUID speaker, Object device) {
        PairLevel level = pairLevels.get(new Pair(speaker, device));
        return level == null ? SignalLevel.NONE : level.link.level();
    }

    /**
     * The squelch tail (DESIGN.md 13.6, 15.2) for the players who were hearing a transmission that just ended: at
     * each device that held it, to the players within the walkie speaker's range of a walkie (D21) or a loudspeaker's
     * of a Transceiver who were hearing that speaker, through it or another device.
     */
    private void squelch(UUID speaker) {
        RadioSnapshot.Transmission tx = published.transmission(speaker);
        if (tx == null) return;
        for (RadioSnapshot.Link link : tx.walkies()) {
            if (!routing.deviceHeldBy(link.device(), tx.channel(), speaker)) continue;
            EntityPlayerMP holder = player(link.holder());
            if (holder == null || !Positions.finite(holder.posX, holder.posY, holder.posZ)) continue;
            playAround(
                speaker,
                tx.channel(),
                holder.dimension,
                holder.posX,
                holder.posY + EYE_HEIGHT,
                holder.posZ,
                tunables.walkieSpeakerRange());
        }
        for (RadioSnapshot.Link link : tx.transceivers()) {
            RadioSnapshot.Transceiver t = link.transceiver();
            if (!t.loudspeaker() || !routing.deviceHeldBy(link.device(), tx.channel(), speaker)) continue;
            playAround(speaker, tx.channel(), t.dimension(), t.x(), t.y(), t.z(), tunables.loudspeakerRange());
        }
    }

    private void playAround(UUID speaker, int channel, int dimension, double x, double y, double z, double range) {
        for (EntityPlayerMP player : players()) {
            if (player.dimension != dimension || !Positions.withinSq(player.getDistanceSq(x, y, z), range)) continue;
            if (player instanceof FakePlayer || player.playerNetServerHandler == null) continue;
            if (!routing.listenerHeldBy(player.getUniqueID(), channel, speaker)) continue;
            // Faded like the voice it ends, to nothing at the range (DESIGN.md 13.5).
            float volume = SpeakerFade.volume(SQUELCH_VOLUME, Math.sqrt(player.getDistanceSq(x, y, z)), range);
            if (volume <= 0) continue;
            player.playerNetServerHandler.sendPacket(new S29PacketSoundEffect(SQUELCH, x, y, z, volume, 1.0f));
        }
    }

    /** What the routing groups route from now. */
    public RadioSnapshot published() {
        return published;
    }

    // endregion

    // region Batteries

    /**
     * Walkies drain their battery while switched on, more while receiving and most while transmitting, and switch off
     * when it's flat (D36, DESIGN.md 11.3). Once a second, the second's worth.
     */
    private void drainWalkies(List<EntityPlayerMP> players) {
        Set<UUID> receiving = new HashSet<>();
        for (UUID speaker : active) {
            RadioSnapshot.Transmission tx = published.transmission(speaker);
            if (tx == null) continue;
            for (RadioSnapshot.Link link : tx.walkies()) receiving.add(link.holder());
        }
        for (EntityPlayerMP player : players) {
            UUID id = player.getUniqueID();
            Carried.Slot activeSlot = ItemWalkieTalkie.active(player);
            Radio radio = radios.get(id);
            boolean transmitting = active.contains(id) && radio != null
                && (radio.transceiver == null || transceivers.get(radio.transceiver) == null);
            for (Carried.Slot slot : Carried.slots(player)) {
                ItemStack stack = slot.stack();
                if (!ItemWalkieTalkie.is(stack) || ItemWalkieTalkie.isTooNew(stack) || !ItemWalkieTalkie.on(stack))
                    continue;
                int perTick = tunables.walkieStandbyDrain();
                if (slot.equals(activeSlot)) {
                    if (transmitting) perTick = tunables.walkieTransmitDrain();
                    else if (receiving.contains(id)) perTick = tunables.walkieReceiveDrain();
                }
                double wanted = (double) perTick * TICKS_PER_SECOND;
                if (wanted <= 0) continue;
                if (ItemWalkieTalkie.drain(stack, wanted) < wanted) ItemWalkieTalkie.setOn(stack, false);
            }
        }
    }

    // endregion

    // region Signal Analyzer

    /** A reading for everyone holding a Signal Analyzer (DESIGN.md 11.4). */
    private void analyzers() {
        for (EntityPlayerMP player : players()) {
            if (!ItemSignalAnalyzer.is(player.getHeldItem())) continue;
            BeComPackets.send(reading(player), player);
        }
    }

    /**
     * What serves a player best on each band, an antenna or the satellites, and every loss on that link; also what
     * {@code /becom signal} says. Radios use VHF; phones use UHF, where rain where the player stands counts (D26), as
     * it does on any sky hop.
     */
    public AnalyzerPacket reading(EntityPlayer player) {
        RadioHops.End end = endOf(player);
        if (end == null) return new AnalyzerPacket(AnalyzerPacket.Reading.NONE, AnalyzerPacket.Reading.NONE);
        UUID id = player.getUniqueID();
        int dimension = player.dimension;
        boolean rains = BeComServer
            .rains(player.worldObj, (int) Math.floor(player.posX), (int) Math.floor(player.posZ));
        double weather = rains ? space.weather(dimension) : 0;
        int satellites = space.count(dimension);
        return new AnalyzerPacket(
            // What a radio hears there: radios stop at Medium (D10).
            reading(
                antennas.best(dimension, end, id, vhf),
                space.losses(dimension, end.depth(), vhf, weather),
                satellites,
                SignalLevel.MEDIUM),
            // What a Mobile Phone gets there, the phone that uses antennas and satellites.
            reading(
                antennas.best(dimension, end, id, antennas.uhf(), rains),
                space.losses(dimension, end.depth(), antennas.uhf(), weather),
                satellites,
                PhoneKind.MOBILE.cap()));
    }

    /** The better of the best antenna and the satellites' sky hop ({@code sky}, null without one). */
    private AnalyzerPacket.Reading reading(AntennaNetwork.Best best, RadioModel.Losses sky, int satellites,
        SignalLevel cap) {
        if (sky != null && (best == null || sky.snr() > best.losses()
            .snr())) {
            return AnalyzerPacket.Reading.sky(satellites, sky, SignalLevel.min(levels.levelOf(sky.snr()), cap));
        }
        if (best == null) return AnalyzerPacket.Reading.NONE;
        RadioModel.Losses losses = best.losses();
        NodeRef ref = best.antenna()
            .ref();
        return AnalyzerPacket.Reading.of(
            BlockPos.x(ref.pos()),
            BlockPos.y(ref.pos()),
            BlockPos.z(ref.pos()),
            best.antenna()
                .tier(),
            losses,
            SignalLevel.min(levels.levelOf(losses.snr()), cap));
    }

    // endregion

    // region Self-test

    // The self-test's fake players aren't in the server's player list, which is where the ticks look; these let it
    // put them through the same code.

    public void refreshForSelfTest(EntityPlayerMP player) {
        refresh(player);
    }

    public void drainForSelfTest(EntityPlayerMP player) {
        drainWalkies(List.of(player));
    }

    /** Stands in for a radio frame from {@code speaker}: the next tick builds their transmission. */
    public void keyForSelfTest(UUID speaker) {
        routing.keyed(speaker, channelOf(speaker), System.nanoTime());
    }

    /**
     * Evaluates every keyed speaker now. A speaker keyed already is otherwise worked out again in their own tick of the
     * second, so a check right after a change could read the snapshot from before it.
     */
    public void publishForSelfTest() {
        keys(System.nanoTime(), new HashSet<>());
        publish(null);
    }

    /**
     * Stands in for {@code speaker} pressing push-to-talk: the publish a press makes, which works out their radio and
     * any other whose transmitter changed, and leaves the rest as they were.
     */
    public void pressForSelfTest(UUID speaker) {
        routing.keyed(speaker, channelOf(speaker), System.nanoTime());
        Set<UUID> pressed = new HashSet<>();
        keys(System.nanoTime(), pressed);
        pressed.add(speaker);
        publish(pressed);
    }

    // endregion

    // region World

    /**
     * Where a player hears and talks from (DESIGN.md 6.1): their eyes, their depth under the surface, their feet. Null
     * when their position isn't a number: a player with no position has no links.
     */
    public static RadioHops.End endOf(EntityPlayer player) {
        if (!Positions.finite(player.posX, player.posY, player.posZ)) return null;
        double eye = player.posY + EYE_HEIGHT;
        int surface = player.worldObj
            .getHeightValue(MathHelper.floor_double(player.posX), MathHelper.floor_double(player.posZ));
        return new RadioHops.End(player.posX, eye, player.posZ, surface - eye, player.posY);
    }

    /** Where a radio block hears and talks from: the top of its block, its depth, and its base. */
    public static RadioHops.End endOf(World world, int x, int y, int z) {
        double top = y + 1;
        return new RadioHops.End(x + 0.5, top, z + 0.5, world.getHeightValue(x, z) - top, y);
    }

    private static boolean noSky(int dimension) {
        WorldServer world = DimensionManager.getWorld(dimension);
        return world != null && world.provider.hasNoSky;
    }

    @SuppressWarnings("unchecked")
    private List<EntityPlayerMP> players() {
        return new ArrayList<>((List<EntityPlayerMP>) server.getConfigurationManager().playerEntityList);
    }

    private EntityPlayerMP player(UUID id) {
        for (Object o : server.getConfigurationManager().playerEntityList) {
            if (o instanceof EntityPlayerMP mp && mp.getUniqueID()
                .equals(id)) return mp;
        }
        return null;
    }

    // endregion
}
