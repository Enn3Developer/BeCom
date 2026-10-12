package com.enn3developer.becom.server;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.MathHelper;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

import com.enn3developer.becom.BeCom;
import com.enn3developer.becom.item.ItemSignalAnalyzer;
import com.enn3developer.becom.item.ItemWalkieTalkie;
import com.enn3developer.becom.logic.DropoutModel;
import com.enn3developer.becom.logic.LevelScale;
import com.enn3developer.becom.logic.SignalLevel;
import com.enn3developer.becom.logic.Tunables;
import com.enn3developer.becom.logic.net.BlockPos;
import com.enn3developer.becom.logic.net.NodeRef;
import com.enn3developer.becom.logic.radio.AntennaMesh;
import com.enn3developer.becom.logic.radio.AntennaNetwork;
import com.enn3developer.becom.logic.radio.Band;
import com.enn3developer.becom.logic.radio.RadioHops;
import com.enn3developer.becom.logic.radio.RadioModel;
import com.enn3developer.becom.packet.AnalyzerPacket;
import com.enn3developer.becom.packet.BeComPackets;
import com.enn3developer.becom.packet.LevelPacket;
import com.enn3developer.becom.quest.BeComQuests;
import com.enn3developer.becom.quest.QuestTask;
import com.enn3developer.becom.voice.RadioGroup;
import com.enn3developer.becom.voice.RadioRouting;
import com.enn3developer.becom.voice.RadioSnapshot;
import com.enn3developer.gtnhvoice.api.server.GtnhVoiceApi;

/**
 * Radio channels in the world (DESIGN.md 8.1, 13.2, 13.3): who carries a working walkie or stands at a Transceiver,
 * their memberships of the sixteen {@code becom:radio.N} groups, and the routing snapshot for whoever holds
 * push-to-talk.
 * Server thread only.
 * <p>
 * Each player's radio and each Transceiver's links to the antennas are refreshed once a second, spread over the twenty
 * ticks (DESIGN.md 6.5). Direct links are worked out only for speakers keyed right now: a speaker shows up in the
 * routing state with their first frame, gets a transmission on the next tick, and is re-evaluated once a second after
 * that. Levels go to clients only when they change (DESIGN.md 13.5).
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
    private final RadioHops hops;
    private final Band vhf;
    private final LevelScale levels;
    private final DropoutModel dropouts;
    private final RadioRouting routing;
    private final RadioGroup[] groups = new RadioGroup[RadioGroup.CHANNELS + 1];

    /** A Transceiver as it last reported, and its links to the antennas. */
    private static final class Transceiver {

        NodeRef ref;
        int channel;
        boolean loudspeaker;
        RadioHops.End end;
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
        int walkieChannel;
        NodeRef transceiver;
        double[] access;
        AntennaNetwork.Mesh accessMesh;
    }

    /** What a speaker transmits from: their walkie, or the Transceiver they stand at (DESIGN.md 8.1). */
    private record Tx(int channel, int dimension, RadioHops.End end, double budget, double[] access, NodeRef from) {}

    private record Pair(UUID speaker, Object device) {}

    private record PairLevel(SignalLevel level, long tick) {}

    private record Heard(UUID listener, UUID speaker) {}

    private record Sent(int channel, SignalLevel level, long tick) {}

    private final Map<NodeRef, Transceiver> transceivers = new HashMap<>();
    private final Map<UUID, Radio> radios = new HashMap<>();
    private final Map<UUID, Integer> joined = new HashMap<>();
    private final Set<UUID> active = new HashSet<>();
    private final Map<Pair, PairLevel> pairLevels = new HashMap<>();
    private final Map<Heard, Sent> sent = new HashMap<>();
    private RadioSnapshot published = RadioSnapshot.EMPTY;
    private long tick;

    public RadioService(MinecraftServer server, Tunables tunables, AntennaNetwork antennas, HeightCaches heights) {
        this.server = server;
        this.tunables = tunables;
        this.antennas = antennas;
        this.heights = heights;
        this.hops = new RadioHops(tunables.radioModel(), tunables.surveyStep());
        this.vhf = tunables.vhf();
        this.levels = tunables.levelScale();
        this.dropouts = tunables.dropoutModel(Tunables.FRAME_MILLIS);
        this.routing = new RadioRouting(
            tunables.directHearingRange(),
            tunables.walkieSpeakerRange(),
            tunables.loudspeakerRange(),
            tunables.captureHoldMillis() * 1_000_000L,
            tunables.captureMargin(),
            () -> java.util.concurrent.ThreadLocalRandom.current()
                .nextDouble(),
            System::nanoTime);
        for (int channel = 1; channel <= RadioGroup.CHANNELS; channel++) {
            groups[channel] = new RadioGroup(
                channel,
                StatCollector.translateToLocalFormatted("becom.group.radio", channel),
                routing);
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
        published = RadioSnapshot.EMPTY;
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
            if (!end.equals(t.end)) t.accessMesh = null;
            t.end = end;
        }
        t.owner = owner;
        t.seenAt = tick;
    }

    public void transceiverGone(NodeRef ref) {
        transceivers.remove(ref);
    }

    private boolean working(Transceiver t) {
        return tick - t.seenAt <= OFFLINE_AFTER_TICKS;
    }

    // endregion

    // region Ticking

    public void tick() {
        tick++;
        long now = System.nanoTime();
        RadioRouting.Contact contact;
        while ((contact = routing.pollContact()) != null) {
            BeComQuests.complete(QuestTask.RADIO_CONTACT, List.of(contact.speaker(), contact.listener()));
        }
        boolean changed = keys(now);
        int slot = (int) (tick % TICKS_PER_SECOND);
        for (EntityPlayerMP player : players()) {
            if (Math.floorMod(
                player.getUniqueID()
                    .hashCode(),
                TICKS_PER_SECOND) == slot) refresh(player);
        }
        Iterator<Transceiver> it = transceivers.values()
            .iterator();
        while (it.hasNext()) {
            Transceiver t = it.next();
            if (!working(t)) {
                it.remove();
                changed = true;
            } else if (Math.floorMod(t.ref.hashCode(), TICKS_PER_SECOND) == slot) {
                refreshAccess(t);
            }
        }
        if (tick % TICKS_PER_SECOND == 0) {
            memberships();
            drainWalkies(players());
            forgetOld(now);
            changed = true;
        }
        if (tick % ((long) tunables.analyzerRefreshSeconds() * TICKS_PER_SECOND) == 0) analyzers();
        if (changed) publish();
    }

    /** Notices speakers pressing and releasing push-to-talk. Returns whether anyone did. */
    private boolean keys(long now) {
        boolean changed = false;
        Map<UUID, RadioRouting.Keyed> keyed = routing.keyedSpeakers();
        for (Map.Entry<UUID, RadioRouting.Keyed> entry : keyed.entrySet()) {
            if (now - entry.getValue()
                .nanos() <= RELEASED_AFTER_NANOS && active.add(entry.getKey())) changed = true;
        }
        Iterator<UUID> it = active.iterator();
        while (it.hasNext()) {
            UUID speaker = it.next();
            RadioRouting.Keyed k = keyed.get(speaker);
            if (k == null || now - k.nanos() > RELEASED_AFTER_NANOS) {
                squelch(speaker);
                it.remove();
                changed = true;
            }
        }
        return changed;
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
        int slot = ItemWalkieTalkie.activeSlot(player.inventory);
        int walkieChannel = slot < 0 ? 0 : ItemWalkieTalkie.channel(player.inventory.getStackInSlot(slot));
        NodeRef at = transceiverAt(player);
        if (walkieChannel == 0 && at == null) {
            radios.remove(id);
            return;
        }
        Radio radio = radios.computeIfAbsent(id, k -> new Radio());
        radio.id = id;
        radio.dimension = player.dimension;
        radio.end = endOf(player);
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
                at.ref);
        }
        if (radio.walkieChannel > 0) {
            return new Tx(
                radio.walkieChannel,
                radio.dimension,
                radio.end,
                tunables.walkieBudget(),
                accessOf(radio),
                null);
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

    /** Builds the routing snapshot for everyone keyed, and sends listeners the levels that changed. */
    private void publish() {
        RadioSnapshot.Builder builder = RadioSnapshot.builder();
        List<UUID> speakers = new ArrayList<>(active);
        speakers.sort(Comparator.naturalOrder());
        List<Radio> receivers = new ArrayList<>(radios.values());
        receivers.sort(Comparator.comparing(r -> r.id));
        List<Transceiver> loudspeakers = new ArrayList<>(transceivers.values());
        loudspeakers.sort(Comparator.comparing(t -> t.ref.pos()));
        for (UUID speaker : speakers) {
            Radio radio = radios.get(speaker);
            Tx tx = radio == null ? null : txOf(radio);
            if (tx == null) continue;
            builder.transmitter(speaker, tx.channel);
            AntennaNetwork.Mesh mesh = antennas.mesh(tx.dimension);
            double[] reach = tx.access != null && tx.access.length == mesh.antennas()
                .size() ? mesh.vhf()
                    .reach(tx.access) : null;
            for (Radio other : receivers) {
                if (other == radio || other.walkieChannel != tx.channel || other.dimension != tx.dimension) continue;
                double snr = snr(tx, other.end, accessOf(other), reach);
                SignalLevel level = level(new Pair(speaker, other.id), snr);
                if (level.atLeast(SignalLevel.LOW)) builder.link(speaker, link(other.id, null, snr));
            }
            for (Transceiver t : loudspeakers) {
                if (!working(t) || t.channel != tx.channel
                    || t.ref.dimension() != tx.dimension
                    || t.ref.equals(tx.from)) continue;
                double snr = snr(tx, t.end, refreshAccess(t), reach);
                SignalLevel level = level(new Pair(speaker, t.ref), snr);
                if (level.atLeast(SignalLevel.LOW)) {
                    builder.link(
                        speaker,
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
        }
        published = builder.build();
        routing.publish(published);
        sendLevels(speakers);
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
     * The better of the direct link and the path through the antennas (DESIGN.md 8.1). A direct link uses the
     * transmitter's budget (6.1); through antennas, every hop uses the antennas' budgets.
     */
    private double snr(Tx tx, RadioHops.End to, double[] access, double[] reach) {
        double direct = Double.NEGATIVE_INFINITY;
        if (tx.end.distanceTo(to) <= tunables.rangeToLow(tx.budget)) {
            direct = hops.direct(heights.terrain(tx.dimension), tx.end, to, tx.budget, vhf, 0, noSky(tx.dimension))
                .snr();
        }
        double via = reach != null && access != null && access.length == reach.length
            ? AntennaMesh.through(reach, access)
            : Double.NEGATIVE_INFINITY;
        return Math.max(direct, via);
    }

    /** The pair's level after this evaluation, with hysteresis, capped at Medium for radios (D10). */
    private SignalLevel level(Pair pair, double snr) {
        PairLevel before = pairLevels.get(pair);
        SignalLevel next = SignalLevel
            .min(levels.next(before == null ? SignalLevel.NONE : before.level, snr), SignalLevel.MEDIUM);
        pairLevels.put(pair, new PairLevel(next, tick));
        return next;
    }

    /**
     * Tells each listener the level they hear each speaker at, in the order {@code route()} sends: their own walkie,
     * then the nearest walkie speaker in the snapshot's order, then a loudspeaker (DESIGN.md 13.3, 13.5).
     */
    private void sendLevels(List<UUID> speakers) {
        double walkieSq = tunables.walkieSpeakerRange() * tunables.walkieSpeakerRange();
        double loudSq = tunables.loudspeakerRange() * tunables.loudspeakerRange();
        List<EntityPlayerMP> online = players();
        for (UUID speaker : speakers) {
            RadioSnapshot.Transmission tx = published.transmission(speaker);
            if (tx == null) continue;
            String group = RadioGroup.nameOf(tx.channel());
            for (EntityPlayerMP listener : online) {
                UUID id = listener.getUniqueID();
                if (id.equals(speaker)) continue;
                SignalLevel level = SignalLevel.NONE;
                for (RadioSnapshot.Link link : tx.walkies()) {
                    if (link.holder()
                        .equals(id)) level = levelOf(speaker, id);
                }
                if (level == SignalLevel.NONE) {
                    for (RadioSnapshot.Link link : tx.walkies()) {
                        EntityPlayerMP holder = player(link.holder());
                        if (holder != null && holder.dimension == listener.dimension
                            && holder.getDistanceSqToEntity(listener) <= walkieSq) {
                            level = levelOf(speaker, link.holder());
                            break;
                        }
                    }
                }
                if (level == SignalLevel.NONE) {
                    for (RadioSnapshot.Link link : tx.transceivers()) {
                        RadioSnapshot.Transceiver t = link.transceiver();
                        if (t.loudspeaker() && t.dimension() == listener.dimension
                            && listener.getDistanceSq(t.x(), t.y(), t.z()) <= loudSq) {
                            level = levelOf(speaker, new NodeRef(t.dimension(), t.pos()));
                            break;
                        }
                    }
                }
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

    private SignalLevel levelOf(UUID speaker, Object device) {
        PairLevel level = pairLevels.get(new Pair(speaker, device));
        return level == null ? SignalLevel.NONE : level.level;
    }

    /** The squelch tail at every device that was receiving a transmission that just ended (DESIGN.md 13.6, 15.2). */
    private void squelch(UUID speaker) {
        RadioSnapshot.Transmission tx = published.transmission(speaker);
        if (tx == null) return;
        for (RadioSnapshot.Link link : tx.walkies()) {
            EntityPlayerMP holder = player(link.holder());
            if (holder != null) holder.worldObj.playSoundAtEntity(holder, SQUELCH, SQUELCH_VOLUME, 1.0f);
        }
        for (RadioSnapshot.Link link : tx.transceivers()) {
            RadioSnapshot.Transceiver t = link.transceiver();
            WorldServer world = DimensionManager.getWorld(t.dimension());
            if (t.loudspeaker() && world != null)
                world.playSoundEffect(t.x(), t.y(), t.z(), SQUELCH, SQUELCH_VOLUME, 1.0f);
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
            int activeSlot = ItemWalkieTalkie.activeSlot(player.inventory);
            Radio radio = radios.get(id);
            boolean transmitting = active.contains(id) && radio != null
                && (radio.transceiver == null || transceivers.get(radio.transceiver) == null);
            ItemStack[] inventory = player.inventory.mainInventory;
            for (int i = 0; i < inventory.length; i++) {
                ItemStack stack = inventory[i];
                if (!ItemWalkieTalkie.is(stack) || !ItemWalkieTalkie.on(stack)) continue;
                int perTick = tunables.walkieStandbyDrain();
                if (i == activeSlot) {
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

    /** The best antenna for a player on VHF and every loss on that link; also what {@code /becom signal} says. */
    public AnalyzerPacket reading(EntityPlayer player) {
        AntennaNetwork.Best best = antennas.best(player.dimension, endOf(player), player.getUniqueID(), vhf);
        if (best == null) return AnalyzerPacket.none();
        RadioModel.Losses losses = best.losses();
        NodeRef ref = best.antenna()
            .ref();
        return new AnalyzerPacket(
            BlockPos.x(ref.pos()),
            BlockPos.y(ref.pos()),
            BlockPos.z(ref.pos()),
            best.antenna()
                .tier(),
            losses,
            levels.levelOf(losses.snr()));
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
     * Evaluates every keyed speaker now. A speaker keyed already is otherwise re-evaluated at the next whole second,
     * so a check right after a change would read the snapshot from before it.
     */
    public void publishForSelfTest() {
        keys(System.nanoTime());
        publish();
    }

    // endregion

    // region World

    /** Where a player hears and talks from (DESIGN.md 6.1): their eyes, their depth under the surface, their feet. */
    public static RadioHops.End endOf(EntityPlayer player) {
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
