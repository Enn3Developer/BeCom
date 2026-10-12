package com.enn3developer.becom.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraftforge.common.UsernameCache;

import com.enn3developer.becom.block.TileTelephone;
import com.enn3developer.becom.logic.DropoutModel;
import com.enn3developer.becom.logic.LevelScale;
import com.enn3developer.becom.logic.SignalLevel;
import com.enn3developer.becom.logic.Tunables;
import com.enn3developer.becom.logic.call.Call;
import com.enn3developer.becom.logic.call.CallBook;
import com.enn3developer.becom.logic.call.CallHistory;
import com.enn3developer.becom.logic.phone.PhoneKind;
import com.enn3developer.becom.logic.phone.PhoneRecord;
import com.enn3developer.becom.logic.phone.PhoneRegistry;
import com.enn3developer.becom.packet.BeComPackets;
import com.enn3developer.becom.packet.LevelPacket;
import com.enn3developer.becom.packet.TonePacket;
import com.enn3developer.becom.quest.BeComQuests;
import com.enn3developer.becom.quest.QuestTask;
import com.enn3developer.becom.voice.CallGroup;
import com.enn3developer.becom.voice.CallSnapshot;
import com.enn3developer.gtnhvoice.api.server.GtnhVoiceApi;

/**
 * Calls in the world (DESIGN.md 10): handsets, dialling, ringing, tones, history, group membership and the routing
 * snapshot, all around the pure {@link CallBook}. Server thread only.
 */
public final class CallService implements CallBook.Listener {

    private static final int TICKS_PER_SECOND = 20;
    private static final long MILLIS_PER_SECOND = 1000L;
    // How often handset cords are checked: often enough that walking off hangs up within a quarter second.
    private static final int CORD_CHECK_TICKS = 5;

    private final MinecraftServer server;
    private final NetworkService network;
    private final PhoneService phones;
    private final CallGroup group;
    private final LevelScale levels;
    private final DropoutModel dropouts;
    private final CallBook book;
    private final Map<Integer, UUID> handsets = new HashMap<>();
    private final Map<UUID, Integer> handsetOf = new HashMap<>();
    // The name each handset holder had as they took it: their calls' history and screens name them by it.
    private final Map<UUID, String> names = new HashMap<>();
    // Per call, the pairs of phones that were in it at the same time, and those whose history is written.
    private final Map<Long, Set<Pair>> talked = new HashMap<>();
    private final Map<Long, Set<Pair>> recorded = new HashMap<>();
    private long ticks;
    private long publishedVersion = -1;

    public CallService(MinecraftServer server, NetworkService network, PhoneService phones, CallGroup group,
        Tunables tunables) {
        this.server = server;
        this.network = network;
        this.phones = phones;
        this.group = group;
        this.levels = tunables.levelScale();
        this.dropouts = tunables.dropoutModel(Tunables.FRAME_MILLIS);
        this.book = new CallBook(
            levels,
            tunables.callAttemptsPerMinute(),
            tunables.ringSeconds() * MILLIS_PER_SECOND,
            (long) (tunables.dropAfterSeconds() * MILLIS_PER_SECOND),
            tunables.sessionGraceSeconds() * MILLIS_PER_SECOND,
            tunables.groupCallSize(),
            this);
    }

    public CallBook book() {
        return book;
    }

    public CallGroup group() {
        return group;
    }

    /**
     * The clock rings, grace periods, drop timers and the rate limit run on: monotonic, so a system clock stepped
     * back can't keep a phone ringing forever, nor one stepped forward expire everything at once.
     */
    private static long now() {
        return System.nanoTime() / 1_000_000L;
    }

    // region Handsets

    /** Who holds phone {@code phone}'s handset, or null. */
    /** Whether a call is ringing {@code phone}, waiting for it to answer. */
    public boolean isRinging(int phone) {
        Call call = book.callOfPhone(phone);
        Call.Leg leg = call == null ? null : call.legOf(phone);
        return leg != null && leg.state() == Call.Leg.State.RINGING;
    }

    public UUID holderOf(int phone) {
        return handsets.get(phone);
    }

    public Integer handsetOf(UUID player) {
        return handsetOf.get(player);
    }

    /**
     * {@code player} picks up the Telephone {@code phone}: answers it if it's ringing; otherwise rings the only other
     * reachable phone, or opens the dialer (DESIGN.md 5.6). Returns whether the dialer should open.
     */
    public boolean pickUp(EntityPlayerMP player, TileTelephone telephone) {
        Pickup pickup = takeHandset(player, telephone);
        if (pickup != Pickup.TAKEN) return false;
        int phone = telephone.number();
        List<Integer> reachable = reachableFrom(phone);
        if (reachable.size() == 1) {
            dial(player, phone, reachable.get(0));
            return false;
        }
        tone(player, TonePacket.Tone.DIAL);
        return true;
    }

    public enum Pickup {
        REFUSED,
        ANSWERED,
        TAKEN
    }

    /** Takes the handset without dialling anything: answers a ringing phone, or just goes off the hook. */
    public Pickup takeHandset(EntityPlayerMP player, TileTelephone telephone) {
        int phone = telephone.number();
        UUID id = player.getUniqueID();
        if (handsets.containsKey(phone) || handsetOf.containsKey(id) || book.callOf(id) != null) return Pickup.REFUSED;
        // Taken from further than the cord reaches, the handset would hang up again on the next cord check.
        if (!telephone.withinCord(player)) {
            tell(player, "becom.phone.too_far");
            return Pickup.REFUSED;
        }
        boolean ringingHere = isRinging(phone);
        if (ringingHere && !hasVoice.test(id)) {
            tell(player, "becom.call.no_voice");
            return Pickup.REFUSED;
        }
        handsets.put(phone, id);
        handsetOf.put(id, phone);
        names.put(id, player.getCommandSenderName());
        telephone.setOffHook(true);
        if (ringingHere) {
            book.answer(id, phone, now());
            return Pickup.ANSWERED;
        }
        return Pickup.TAKEN;
    }

    /** {@code player} puts the handset down: hangs up whatever call it carries. */
    public void hangUpHandset(UUID player) {
        Integer phone = handsetOf.remove(player);
        if (phone == null) return;
        handsets.remove(phone);
        TileTelephone telephone = phones.telephone(phone);
        if (telephone != null) telephone.setOffHook(false);
        EntityPlayerMP mp = player(player);
        if (mp != null) tone(mp, TonePacket.Tone.HANG_UP);
        book.hangUp(player);
        // After the call ends, so its history still has the name.
        names.remove(player);
    }

    /** A Telephone was broken or removed: its call ends and its handset is let go. */
    public void phoneGone(int phone) {
        UUID holder = handsets.remove(phone);
        if (holder != null) {
            handsetOf.remove(holder);
            EntityPlayerMP mp = player(holder);
            if (mp != null) tone(mp, TonePacket.Tone.HANG_UP);
        }
        book.phoneGone(phone);
        // After the call ends, so its history still has the name.
        if (holder != null) names.remove(holder);
    }

    // endregion

    // region Dialling

    /** The phones {@code phone} can reach at Low or better, other than itself. */
    public List<Integer> reachableFrom(int phone) {
        List<Integer> reachable = new ArrayList<>();
        double low = levels.threshold(SignalLevel.LOW);
        for (Map.Entry<Integer, Double> entry : network.graph()
            .phonesFrom(phone)
            .entrySet()) {
            if (entry.getValue() >= low && phones.record(entry.getKey()) != null) reachable.add(entry.getKey());
        }
        return reachable;
    }

    /** {@code player}, holding phone {@code from}'s handset, dials {@code to}. */
    public CallBook.DialResult dial(EntityPlayerMP player, int from, int to) {
        UUID id = player.getUniqueID();
        Integer held = handsetOf.get(id);
        if (held == null || held != from) return CallBook.DialResult.INVALID;
        CallBook.Check check = worldCheck(player, from, to);
        CallBook.DialResult result = book.dial(id, from, to, now(), check);
        switch (result) {
            case RINGING -> tone(player, TonePacket.Tone.RINGBACK);
            case BUSY -> tone(player, TonePacket.Tone.BUSY);
            case UNREACHABLE -> tone(player, TonePacket.Tone.UNREACHABLE);
            case RATE_LIMITED -> {
                tell(player, "becom.call.rate_limited");
                tone(player, TonePacket.Tone.UNREACHABLE);
            }
            case INVALID -> tell(player, "becom.call.invalid");
        }
        return result;
    }

    private CallBook.Check worldCheck(EntityPlayerMP caller, int from, int to) {
        if (!hasVoice.test(caller.getUniqueID())) {
            tell(caller, "becom.call.no_voice");
            return CallBook.Check.UNREACHABLE;
        }
        PhoneRegistry registry = phones.registry();
        if (registry.refusal(caller.getUniqueID(), to) != PhoneRegistry.Refusal.NONE) return CallBook.Check.UNREACHABLE;
        // Someone holding the other handset without a call is dialling out (DESIGN.md 10: busy).
        if (handsets.containsKey(to)) return CallBook.Check.BUSY;
        double width = network.graph()
            .width(from, to);
        if (width < levels.threshold(SignalLevel.LOW)) return CallBook.Check.UNREACHABLE;
        return CallBook.Check.OK;
    }

    // endregion

    // region CallBook.Listener

    @Override
    public void ringing(Call call, Call.Leg leg) {
        TileTelephone telephone = phones.telephone(leg.phone());
        if (telephone != null) telephone.setRinging(true);
    }

    @Override
    public void connected(Call call, Call.Leg answered) {
        TileTelephone telephone = phones.telephone(answered.phone());
        if (telephone != null) telephone.setRinging(false);
        Set<Pair> pairs = talked.computeIfAbsent(call.id(), id -> new HashSet<>());
        for (Call.Leg other : call.activeLegs()) {
            if (other != answered) pairs.add(Pair.of(answered.phone(), other.phone()));
        }
        List<UUID> players = call.participants();
        for (UUID player : players) {
            GtnhVoiceApi.groupManager()
                .join(player, group);
            EntityPlayerMP mp = player(player);
            if (mp != null) tone(mp, TonePacket.Tone.NONE);
        }
        BeComQuests.complete(QuestTask.FIRST_CALL, players);
        // Levels now, not at the next whole second: until a direction has a level it gets no route, and "hello?" is
        // lost.
        book.evaluate(call, now(), quality);
        publish();
    }

    @Override
    public void legLeft(Call call, Call.Leg leg, Set<UUID> players, CallBook.EndReason reason) {
        TileTelephone telephone = phones.telephone(leg.phone());
        if (telephone != null) telephone.setRinging(false);
        // Its rows are written now, while everyone's names are still known; the rest when the call ends.
        for (Call.Leg other : call.legs()) if (other != leg) record(call, leg, other);
        phones.registry()
            .changed();
        List<UUID> stay = call.participants();
        for (UUID player : players) leaveCall(player, stay, reason);
        for (UUID player : stay) {
            EntityPlayerMP mp = player(player);
            if (mp == null) continue;
            for (UUID gone : players) BeComPackets.send(new LevelPacket(gone, CallGroup.NAME, SignalLevel.NONE), mp);
        }
        publish();
    }

    @Override
    public void ended(Call call, CallBook.EndReason reason) {
        for (Call.Leg leg : call.legs()) {
            TileTelephone telephone = phones.telephone(leg.phone());
            if (telephone != null) telephone.setRinging(false);
        }
        for (Call.Leg leg : call.legs()) {
            for (Call.Leg other : call.legs()) if (other != leg) record(call, leg, other);
        }
        talked.remove(call.id());
        recorded.remove(call.id());
        phones.registry()
            .changed();
        List<UUID> players = call.participants();
        for (UUID player : players) leaveCall(player, players, reason);
        publish();
    }

    /** {@code player} is out of their call: out of the group, the others' levels cleared, and told why. */
    private void leaveCall(UUID player, List<UUID> others, CallBook.EndReason reason) {
        GtnhVoiceApi.groupManager()
            .leave(player, group);
        EntityPlayerMP mp = player(player);
        if (mp == null) return;
        for (UUID other : others) {
            if (!other.equals(player)) BeComPackets.send(new LevelPacket(other, CallGroup.NAME, SignalLevel.NONE), mp);
        }
        if (reason == CallBook.EndReason.NO_ANSWER) tell(mp, "becom.call.no_answer");
        if (reason == CallBook.EndReason.DROPPED) tell(mp, "becom.call.dropped");
        // Whoever still holds a handset hears the line go dead; whoever hung up already heard the click.
        if (handsetOf.containsKey(player)) tone(mp, TonePacket.Tone.BUSY);
    }

    /**
     * Writes the row {@code leg}'s phone keeps about {@code other}'s, and the one {@code other} keeps about it, once
     * per call: when the first of the two leaves or the call ends. History keeps wall-clock time: it's a date, saved
     * across restarts, not a timer.
     */
    private void record(Call call, Call.Leg leg, Call.Leg other) {
        Pair pair = Pair.of(leg.phone(), other.phone());
        if (!recorded.computeIfAbsent(call.id(), id -> new HashSet<>())
            .add(pair)) return;
        boolean together = talked.getOrDefault(call.id(), Set.of())
            .contains(pair);
        long time = System.currentTimeMillis();
        addRow(leg, other, CallHistory.direction(leg, other, together), time);
        addRow(other, leg, CallHistory.direction(other, leg, together), time);
    }

    private void addRow(Call.Leg own, Call.Leg other, PhoneRecord.Direction direction, long time) {
        PhoneRecord record = phones.record(own.phone());
        if (record == null || direction == null) return;
        // Who was on the other end, so blocking this row blocks whoever called, whosever phone it was. With nobody
        // there, the phone's owner now: its number can be freed and given to someone else later.
        PhoneRecord otherPhone = phones.record(other.phone());
        UUID otherPlayer = other.player() != null ? other.player() : otherPhone == null ? null : otherPhone.owner();
        String name = other.player() != null ? nameOf(other.player(), phones.name(other.phone()))
            : phones.name(other.phone());
        record.addHistory(new PhoneRecord.HistoryEntry(time, other.phone(), name, direction, otherPlayer));
    }

    /** Two phones of a call, either way round. */
    private record Pair(int low, int high) {

        static Pair of(int a, int b) {
            return a <= b ? new Pair(a, b) : new Pair(b, a);
        }
    }

    @Override
    public void levelChanged(Call call, UUID speaker, UUID listener, SignalLevel level) {
        EntityPlayerMP mp = player(listener);
        if (mp != null) BeComPackets.send(new LevelPacket(speaker, CallGroup.NAME, level), mp);
    }

    @Override
    public void roomChanged(Call call, Call.Leg leg, Set<UUID> joined, Set<UUID> left) {
        List<UUID> players = call.participants();
        for (UUID player : left) leaveCall(player, players, null);
        for (UUID player : joined) {
            GtnhVoiceApi.groupManager()
                .join(player, group);
        }
        // Newcomers hear and are heard at the levels the speakerphone already has.
        for (Call.Leg other : call.activeLegs()) {
            if (other == leg) continue;
            SignalLevel in = call.level(other, leg);
            SignalLevel out = call.level(leg, other);
            for (UUID player : joined) {
                EntityPlayerMP mp = player(player);
                for (UUID far : other.players()) {
                    if (mp != null) BeComPackets.send(new LevelPacket(far, CallGroup.NAME, in), mp);
                    EntityPlayerMP farMp = player(far);
                    if (farMp != null) BeComPackets.send(new LevelPacket(player, CallGroup.NAME, out), farMp);
                }
            }
            for (UUID player : left) {
                for (UUID far : other.players()) {
                    EntityPlayerMP farMp = player(far);
                    if (farMp != null)
                        BeComPackets.send(new LevelPacket(player, CallGroup.NAME, SignalLevel.NONE), farMp);
                }
            }
        }
        publish();
    }

    // endregion

    // region Lifecycle

    public void tick() {
        ticks++;
        long now = now();
        book.tick(now);
        if (ticks % CORD_CHECK_TICKS == 0) checkCords();
        if (ticks % TICKS_PER_SECOND == 0) {
            book.evaluate(now, quality);
            // SNR moves dropout rates even when no level changes, so the snapshot is rebuilt every evaluation.
            publish();
        } else if (book.version() != publishedVersion) {
            publish();
        }
    }

    // Whether a player has a voice session (DESIGN.md 13.2). Only the self-test swaps it, for its fake players.
    private Predicate<UUID> hasVoice = id -> GtnhVoiceApi.session(id)
        .isPresent();

    /** The self-test's fake players have no voice; this lets it dial and answer through the real checks. */
    public void voiceForSelfTest(Predicate<UUID> hasVoice) {
        this.hasVoice = hasVoice;
    }

    /** A direction's SNR is its best path's weakest hop; its level is capped by both phones (DESIGN.md 4.1). */
    private final CallBook.Quality quality = new CallBook.Quality() {

        @Override
        public double snr(Call call, Call.Leg speaker, Call.Leg listener) {
            return network.graph()
                .width(speaker.phone(), listener.phone());
        }

        @Override
        public SignalLevel cap(Call call, Call.Leg speaker, Call.Leg listener) {
            return SignalLevel.min(capOf(speaker.phone()), capOf(listener.phone()));
        }
    };

    private SignalLevel capOf(int phone) {
        PhoneRecord record = phones.record(phone);
        return record == null ? SignalLevel.NONE
            : record.kind()
                .cap();
    }

    private void checkCords() {
        for (Map.Entry<Integer, UUID> entry : new ArrayList<>(handsets.entrySet())) {
            EntityPlayerMP player = player(entry.getValue());
            TileTelephone telephone = phones.telephone(entry.getKey());
            if (player == null || telephone == null || !telephone.withinCord(player)) {
                hangUpHandset(entry.getValue());
            }
        }
    }

    /** The player logged out (DESIGN.md 10): their call ends and their handset drops. */
    public void logout(UUID player) {
        hangUpHandset(player);
        book.logout(player);
    }

    /** sessionStarted: a player in a call gets their membership back (DESIGN.md 13.2). */
    public void sessionStarted(UUID player) {
        book.sessionBack(player);
        Call call = book.callOf(player);
        if (call != null && call.state() == Call.State.CONNECTED) {
            GtnhVoiceApi.groupManager()
                .join(player, group);
        }
    }

    public void sessionTimedOut(UUID player) {
        book.sessionLost(player, now());
    }

    public void stop() {
        for (Call call : new ArrayList<>(book.calls())) book.endByAdmin(call);
        // Handsets go back on the hook while the worlds are still loaded, so none is saved as missing.
        for (Map.Entry<Integer, UUID> entry : new ArrayList<>(handsets.entrySet())) {
            TileTelephone telephone = phones.telephone(entry.getKey());
            if (telephone != null) telephone.setOffHook(false);
        }
        handsets.clear();
        handsetOf.clear();
        names.clear();
        talked.clear();
        recorded.clear();
        group.clear();
    }

    // endregion

    // region Snapshot

    private void publish() {
        CallSnapshot.Builder builder = CallSnapshot.builder();
        for (Call call : book.calls()) {
            if (call.state() != Call.State.CONNECTED) continue;
            List<Call.Leg> active = call.activeLegs();
            for (Call.Leg from : active) {
                for (Call.Leg to : active) {
                    if (from == to || !call.level(from, to)
                        .atLeast(SignalLevel.LOW)) continue;
                    double loss = dropouts.lossRate(call.snr(from, to));
                    for (UUID speaker : from.players()) {
                        for (UUID listener : to.players()) {
                            builder.route(
                                speaker,
                                new CallSnapshot.Route(
                                    listener,
                                    dropouts.enterBadChance(loss),
                                    dropouts.leaveBadChance()));
                        }
                    }
                }
            }
        }
        group.publish(builder.build());
        publishedVersion = book.version();
    }

    // endregion

    /** The kind of phone a number is, for the screens. */
    public PhoneKind kindOf(int phone) {
        PhoneRecord record = phones.record(phone);
        return record == null ? null : record.kind();
    }

    /** A player's name: as they took their handset, else online, else the last one Forge saw, else {@code fallback}. */
    public String nameOf(UUID id, String fallback) {
        String held = names.get(id);
        if (held != null) return held;
        EntityPlayerMP online = player(id);
        if (online != null) return online.getCommandSenderName();
        String cached = UsernameCache.getLastKnownUsername(id);
        return cached != null ? cached : fallback;
    }

    private EntityPlayerMP player(UUID id) {
        if (id == null) return null;
        for (Object o : server.getConfigurationManager().playerEntityList) {
            if (o instanceof EntityPlayerMP mp && mp.getUniqueID()
                .equals(id)) return mp;
        }
        return null;
    }

    public EntityPlayerMP onlinePlayer(UUID id) {
        return player(id);
    }

    private static void tone(EntityPlayerMP player, TonePacket.Tone tone) {
        BeComPackets.send(new TonePacket(tone), player);
    }

    private static void tell(EntityPlayerMP player, String key, Object... args) {
        player.addChatComponentMessage(new ChatComponentTranslation(key, args));
    }
}
