package com.enn3developer.becom.server;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.S29PacketSoundEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.UsernameCache;

import com.cleanroommc.modularui.factory.GuiFactories;
import com.enn3developer.becom.BeCom;
import com.enn3developer.becom.block.TileTelephone;
import com.enn3developer.becom.item.Carried;
import com.enn3developer.becom.item.ItemCarriedPhone;
import com.enn3developer.becom.logic.DropoutModel;
import com.enn3developer.becom.logic.LevelScale;
import com.enn3developer.becom.logic.PhoneNumbers;
import com.enn3developer.becom.logic.Positions;
import com.enn3developer.becom.logic.SignalLevel;
import com.enn3developer.becom.logic.Tunables;
import com.enn3developer.becom.logic.call.Call;
import com.enn3developer.becom.logic.call.CallBook;
import com.enn3developer.becom.logic.call.CallHistory;
import com.enn3developer.becom.logic.call.CallQuests;
import com.enn3developer.becom.logic.net.CallGraph;
import com.enn3developer.becom.logic.phone.PhoneKind;
import com.enn3developer.becom.logic.phone.PhoneRecord;
import com.enn3developer.becom.logic.phone.PhoneRegistry;
import com.enn3developer.becom.packet.BeComPackets;
import com.enn3developer.becom.packet.CallBarsPacket;
import com.enn3developer.becom.packet.LevelPacket;
import com.enn3developer.becom.packet.RingPacket;
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
    // A Videophone not heard from for this long is unpowered or unloaded, like a node offline (DESIGN.md 14).
    private static final int VIDEOPHONE_LOST_TICKS = 2 * TICKS_PER_SECOND;
    /** How long a busy tone plays on a phone with no handset to put down before it stops by itself. */
    public static final int BRIEF_TONE_TICKS = 3 * TICKS_PER_SECOND;

    private final MinecraftServer server;
    private final NetworkService network;
    private final PhoneService phones;
    private final CarriedPhones carried;
    private final CallGroup group;
    private final Tunables tunables;
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
    // Carried phones ringing now, and the tick each started (DESIGN.md 11.2: a carried phone rings from its player).
    private final Map<Integer, Long> ringingCarried = new HashMap<>();
    // Each ringing carried phone's notice and whose phone it is: it goes from their screen however the phone left them.
    // A client shows one notice: the latest ringing for its player, while they're free to answer it.
    private final Map<Integer, Notice> notices = new HashMap<>();
    private long noticesShown;
    // The phone whose notice each player's screen shows now.
    private final Map<UUID, Integer> shownNotices = new HashMap<>();
    // Working Videophones, where they stand, and the tick each last reported in (DESIGN.md 10.2).
    private final Map<Integer, Videophone> videophones = new HashMap<>();
    // The level each player around a Videophone's loudspeaker was last told they hear each speaker at (DESIGN.md 13.5).
    private final Map<Heard, SignalLevel> loudspeakerLevels = new HashMap<>();
    // The bars each player in a call was last told: the weakest level they hear their call at (DESIGN.md 11.2).
    private final Map<UUID, SignalLevel> callBars = new HashMap<>();
    // Players hearing a tone that stops by itself, and the tick it stops.
    private final Map<UUID, Long> toneStops = new HashMap<>();
    // The last tone each player was played, the last thing they were told and whether a ringing notice shows for them,
    // kept only while the self-test asks.
    private Map<UUID, TonePacket.Tone> toneLog;
    private Map<UUID, String> messageLog;
    private Map<UUID, Boolean> noticeLog;
    private long ticks;
    private long publishedVersion = -1;

    public CallService(MinecraftServer server, NetworkService network, PhoneService phones, CarriedPhones carried,
        CallGroup group, Tunables tunables) {
        this.server = server;
        this.network = network;
        this.phones = phones;
        this.carried = carried;
        this.group = group;
        this.tunables = tunables;
        this.levels = tunables.levelScale();
        this.dropouts = tunables.dropoutModel(Tunables.FRAME_MILLIS);
        this.book = new CallBook(
            levels,
            tunables.callAttemptsPerMinute(),
            tunables.ringSeconds() * MILLIS_PER_SECOND,
            (long) (tunables.dropAfterSeconds() * MILLIS_PER_SECOND),
            tunables.sessionGraceSeconds() * MILLIS_PER_SECOND,
            tunables.groupCallSize(),
            tunables.videophoneEmptySeconds() * MILLIS_PER_SECOND,
            this);
        carried.setCalls(new CarriedPhones.Calls() {

            @Override
            public boolean inCall(int number) {
                return book.callOfPhone(number) != null;
            }

            @Override
            public void lost(int number) {
                // Switched off, flat, dropped or stored: it can't ring or carry a call any more (DESIGN.md 11.3).
                book.phoneGone(number);
            }
        });
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

    /** Whether a call is ringing {@code phone}, waiting for it to answer. */
    public boolean isRinging(int phone) {
        Call call = book.callOfPhone(phone);
        Call.Leg leg = call == null ? null : call.legOf(phone);
        return leg != null && leg.state() == Call.Leg.State.RINGING;
    }

    /** Who holds phone {@code phone}'s handset, or null. */
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
        showNotice(id);
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
        // Leaving a call clicks as they leave it; with no call, the click is here.
        if (!book.hangUp(player)) tone(player, TonePacket.Tone.HANG_UP);
        showNotice(player);
        // After the call ends, so its history still has the name.
        names.remove(player);
    }

    /** A phone block was broken or removed: its call ends and a Telephone's handset is let go. */
    public void phoneGone(int phone) {
        videophones.remove(phone);
        UUID holder = handsets.remove(phone);
        if (holder != null) handsetOf.remove(holder);
        boolean inCall = holder != null && book.callOf(holder) != null;
        book.phoneGone(phone);
        if (holder != null) {
            // Leaving the call clicked already.
            if (!inCall) tone(holder, TonePacket.Tone.HANG_UP);
            showNotice(holder);
            // After the call ends, so its history still has the name.
            names.remove(holder);
        }
    }

    // endregion

    // region Dialling

    /**
     * Where a phone joins the call graph: a carried one at whatever gives it service where its player stands, none
     * when it has no service; a phone on a line at its own vertex.
     */
    public List<CallGraph.Entry> entriesOf(int phone) {
        PhoneRecord record = phones.record(phone);
        if (record != null && record.kind()
            .carried()) {
            return carried.serviceOf(phone)
                .entries();
        }
        return CallGraph.Entry.of(phone);
    }

    /** The weakest hop on the best path from one phone to another, whatever kind each is. */
    public double width(int from, int to) {
        return network.graph()
            .width(entriesOf(from), entriesOf(to));
    }

    /** A phone's reachable phones, and the tick they were found. */
    private record Reachable(long tick, List<Integer> phones) {}

    // Each phone's reachable phones, kept a second: an open phone screen asks for its contacts every tick, and each
    // answer is a path search per phone in reach (DESIGN.md 14: path finding re-runs only when something changes).
    private final Map<Integer, Reachable> reachable = new HashMap<>();
    private int reachableSearches;

    /**
     * The phones {@code phone} can reach at Low or better, other than itself: on lines, and carried ones. Found at most
     * once a second per phone.
     */
    public List<Integer> reachableFrom(int phone) {
        Reachable known = reachable.get(phone);
        if (known != null && ticks - known.tick() < TICKS_PER_SECOND) return known.phones();
        List<Integer> found = List.copyOf(searchReachable(phone));
        reachable.put(phone, new Reachable(ticks, found));
        return found;
    }

    /** How many times reachable phones were searched for; for the self-test. */
    public int reachableSearchesForSelfTest() {
        return reachableSearches;
    }

    private List<Integer> searchReachable(int phone) {
        reachableSearches++;
        List<Integer> found = new ArrayList<>();
        double low = levels.threshold(SignalLevel.LOW);
        List<CallGraph.Entry> own = entriesOf(phone);
        CallGraph graph = network.graph();
        for (Map.Entry<Integer, Double> entry : graph.phonesFrom(own)
            .entrySet()) {
            PhoneRecord record = phones.record(entry.getKey());
            if (entry.getValue() < low || record == null) continue;
            // An unpowered or unloaded Videophone can't be called (D11).
            if (record.kind() == PhoneKind.VIDEOPHONE && !videophoneWorking(entry.getKey())) continue;
            found.add(entry.getKey());
        }
        for (int other : carried.working()) {
            if (other != phone && phones.record(other) != null && graph.width(own, entriesOf(other)) >= low) {
                found.add(other);
            }
        }
        return found;
    }

    /**
     * {@code player}, holding phone {@code from}'s handset, dials {@code to}; or, while they're in a call on it, adds
     * {@code to} to the call (group calls).
     */
    public CallBook.DialResult dial(EntityPlayerMP player, int from, int to) {
        UUID id = player.getUniqueID();
        Integer held = handsetOf.get(id);
        if (held == null || held != from) return CallBook.DialResult.INVALID;
        Call call = book.callOf(id);
        if (call != null) return add(player, call, from, to);
        CallBook.DialResult result = book.dial(id, from, to, now(), () -> worldCheck(player, from, to));
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
        PhoneRecord target = registry.get(to);
        if (target.kind()
            .carried()) {
            // Unreachable when nobody carries it switched on and charged, or its holder has no voice (DESIGN.md 10);
            // busy when its holder is in a call on another phone.
            UUID holder = carried.holderOf(to);
            if (holder == null || !carried.working()
                .contains(to) || !hasVoice.test(holder)) return CallBook.Check.UNREACHABLE;
            if (book.callOf(holder) != null || handsetOf.containsKey(holder)) return CallBook.Check.BUSY;
        }
        if (target.kind() == PhoneKind.VIDEOPHONE && !videophoneWorking(to)) return CallBook.Check.UNREACHABLE;
        // Someone holding the other handset without a call is dialling out (DESIGN.md 10: busy).
        if (handsets.containsKey(to)) return CallBook.Check.BUSY;
        if (width(from, to) < levels.threshold(SignalLevel.LOW)) return CallBook.Check.UNREACHABLE;
        return CallBook.Check.OK;
    }

    // endregion

    // region Carried phones

    /**
     * Whether {@code player} carries phone {@code number} right now: one handed to someone else is theirs as soon as
     * the
     * server has seen it in their hands, though the old holder's list catches up only at their next look.
     */
    public boolean carries(EntityPlayer player, int number) {
        UUID id = player.getUniqueID();
        return id.equals(carried.holderOf(number)) && carried.carriedBy(id)
            .contains(number);
    }

    /**
     * {@code player} dials {@code to} from the phone they carry (DESIGN.md 11.2): a call, or, while they're in one on
     * it, another phone added to it (group calls).
     */
    public CallBook.DialResult dialCarried(EntityPlayerMP player, int from, int to) {
        if (!carries(player, from) || !carried.working()
            .contains(from)) return CallBook.DialResult.INVALID;
        UUID id = player.getUniqueID();
        if (holdsHandset(player)) return CallBook.DialResult.INVALID;
        Call call = book.callOf(id);
        if (call != null) return add(player, call, from, to);
        CallBook.DialResult result = book.dial(id, from, to, now(), () -> worldCheck(player, from, to));
        report(player, result);
        return result;
    }

    /**
     * {@code player}, in {@code call} on phone {@code from}, adds {@code to} to it. Its ringing makes no tone: the call
     * goes on meanwhile.
     */
    private CallBook.DialResult add(EntityPlayerMP player, Call call, int from, int to) {
        Call.Leg own = call.legOf(player.getUniqueID());
        if (own == null || own.phone() != from) return CallBook.DialResult.INVALID;
        CallBook.DialResult result = book.add(player.getUniqueID(), to, now(), () -> worldCheck(player, from, to));
        if (result != CallBook.DialResult.RINGING) report(player, result);
        return result;
    }

    /**
     * Tells someone dialling from a carried phone or a Videophone how it went. With no handset to put down, a busy tone
     * stops by itself; a ringback stops as the call connects or ends.
     */
    private void report(EntityPlayerMP player, CallBook.DialResult result) {
        switch (result) {
            case RINGING -> tone(player, TonePacket.Tone.RINGBACK);
            case BUSY -> briefTone(player.getUniqueID(), TonePacket.Tone.BUSY);
            case UNREACHABLE -> tone(player, TonePacket.Tone.UNREACHABLE);
            case RATE_LIMITED -> {
                tell(player, "becom.call.rate_limited");
                tone(player, TonePacket.Tone.UNREACHABLE);
            }
            case INVALID -> tell(player, "becom.call.invalid");
            case FULL -> tell(player, "becom.call.full", book.groupSize());
        }
    }

    /** The carried phone ringing for {@code player}: the one their notice names, or the first they carry; or -1. */
    public int ringingFor(EntityPlayer player) {
        Notice shown = latestNotice(player.getUniqueID());
        if (shown != null && carries(player, shown.phone()) && isRinging(shown.phone())) return shown.phone();
        for (int number : carried.carriedBy(player.getUniqueID())) {
            if (isRinging(number) && carries(player, number)) return number;
        }
        return -1;
    }

    /** A ringing carried phone's notice: whose screen shows it, what it says, and in which order it came. */
    private record Notice(int phone, UUID holder, RingPacket packet, long order) {}

    /** The latest notice of a phone ringing for {@code holder} that they still carry, or null. */
    private Notice latestNotice(UUID holder) {
        Notice latest = null;
        for (Notice notice : notices.values()) {
            if (notice.holder()
                .equals(holder) && holder.equals(carried.holderOf(notice.phone()))
                && (latest == null || notice.order() > latest.order())) latest = notice;
        }
        return latest;
    }

    /** {@code player} answers phone {@code number}, which they carry and which rings. */
    public boolean answerCarried(EntityPlayerMP player, int number) {
        if (!carries(player, number) || !isRinging(number) || holdsHandset(player)) return false;
        if (!hasVoice.test(player.getUniqueID())) {
            tell(player, "becom.call.no_voice");
            return false;
        }
        return book.answer(player.getUniqueID(), number, now());
    }

    /** {@code player} turns down a call ringing phone {@code number}, which they carry (D34). */
    public boolean rejectCarried(EntityPlayer player, int number) {
        return carries(player, number) && book.reject(number);
    }

    /**
     * Whether {@code player} holds a Telephone's handset, and is told to put it down: a call on a phone they carry
     * would be tied to its cord, and end as they walk off or hang the handset up.
     */
    private boolean holdsHandset(EntityPlayerMP player) {
        if (!handsetOf.containsKey(player.getUniqueID())) return false;
        tell(player, "becom.phone.handset_first");
        return true;
    }

    /** {@code player} hangs up whatever call they're in: a handset goes back, a carried phone leaves its call. */
    public void hangUp(EntityPlayerMP player) {
        UUID id = player.getUniqueID();
        if (handsetOf.containsKey(id)) {
            hangUpHandset(id);
            return;
        }
        // Leaving the call clicks.
        book.hangUp(id);
    }

    /**
     * The Phone key (DESIGN.md 11.1, D34): with sneak, turns down the player's ringing phone; hangs up during a call,
     * or puts a handset down; answers the ringing phone; otherwise opens the screen of the first phone they carry. A
     * ringing phone is one they carry, or a Telephone or Videophone close enough to answer: within its cord, or in its
     * room. Hanging up comes before answering: a call they're in has to end before another can be answered.
     */
    public void phoneKey(EntityPlayerMP player, boolean sneaking) {
        int ringing = ringingFor(player);
        if (ringing >= 0 && sneaking) {
            rejectCarried(player, ringing);
            return;
        }
        UUID id = player.getUniqueID();
        if (book.callOf(id) != null || handsetOf.containsKey(id)) {
            hangUp(player);
            return;
        }
        if (ringing >= 0) {
            answerCarried(player, ringing);
            return;
        }
        if (answerNear(player, sneaking)) return;
        Carried.Slot slot = Carried.first(player, ItemCarriedPhone::is);
        if (slot == null || !PhoneScreenActions.mayOpenScreen(player)) return;
        if (slot.inventory() == player.inventory) {
            GuiFactories.playerInventory()
                .openFromPlayerInventory(player, slot.index());
        } else {
            GuiFactories.playerInventory()
                .openFromBaubles(player, slot.index());
        }
    }

    /** Answers, or with sneak turns down, a ringing Telephone or Videophone the player is close enough to answer. */
    private boolean answerNear(EntityPlayerMP player, boolean sneaking) {
        for (Call call : new ArrayList<>(book.calls())) {
            for (Call.Leg leg : call.ringingLegs()) {
                int phone = leg.phone();
                TileTelephone telephone = phones.telephone(phone);
                Videophone videophone = kindOf(phone) == PhoneKind.VIDEOPHONE ? videophone(phone) : null;
                boolean near = telephone != null && telephone.withinCord(player)
                    || videophone != null && inRoom(player, videophone);
                if (!near) continue;
                if (sneaking) book.reject(phone);
                else if (telephone != null) takeHandset(player, telephone);
                else answerVideophone(player, phone);
                return true;
            }
        }
        return false;
    }

    /**
     * Starts or keeps a carried phone ringing at its holder: everyone near hears it, or in silent mode only they do.
     */
    private void ringCarried(int number) {
        Long since = ringingCarried.get(number);
        if (since == null || (ticks - since) % TileTelephone.RING_PERIOD_TICKS != 0) return;
        EntityPlayerMP holder = player(carried.holderOf(number));
        PhoneRecord record = phones.record(number);
        if (holder == null || record == null) return;
        if (record.silent()) {
            // A holder whose position isn't a number has none to buzz at.
            if (!Positions.finite(holder.posX, holder.posY, holder.posZ)) return;
            holder.playerNetServerHandler.sendPacket(
                new S29PacketSoundEffect(
                    BeCom.MODID + ":phone.buzz",
                    holder.posX,
                    holder.posY,
                    holder.posZ,
                    1.0f,
                    1.0f));
        } else {
            holder.worldObj.playSoundAtEntity(holder, BeCom.MODID + ":phone.ring", 1.0f, 1.0f);
        }
    }

    private void stopRinging(int number) {
        ringingCarried.remove(number);
        Notice gone = notices.remove(number);
        // Another phone still ringing for them shows instead.
        if (gone != null) showNotice(gone.holder());
    }

    /**
     * Puts on {@code holder}'s screen the notice they should see: the latest of their phones ringing, unless they're in
     * a call or hold a handset, where the Phone key hangs up instead of answering (DESIGN.md 11.1). The phone rings on.
     */
    private void showNotice(UUID holder) {
        boolean busy = book.callOf(holder) != null || handsetOf.containsKey(holder);
        Notice wanted = busy ? null : latestNotice(holder);
        Integer shown = shownNotices.get(holder);
        if (wanted == null ? shown == null : shown != null && shown == wanted.phone()) return;
        if (wanted == null) shownNotices.remove(holder);
        else shownNotices.put(holder, wanted.phone());
        if (noticeLog != null) noticeLog.put(holder, wanted != null);
        EntityPlayerMP mp = player(holder);
        if (mp != null) BeComPackets.send(wanted != null ? wanted.packet() : RingPacket.stop(), mp);
    }

    // endregion

    // region Videophones

    /** A working Videophone: where it stands and faces, and the tick it last reported in (DESIGN.md 10.2). */
    public record Videophone(int dimension, int x, int y, int z, int facing, long heard) {

        public double centerX() {
            return x + 0.5;
        }

        public double centerY() {
            return y + 0.5;
        }

        public double centerZ() {
            return z + 0.5;
        }
    }

    /** A listener and a speaker they hear. */
    private record Heard(UUID listener, UUID speaker) {}

    /** A Videophone reports in each tick it's loaded and powered (D11). */
    public void videophoneHeard(int number, int dimension, int x, int y, int z, int facing) {
        videophones.put(number, new Videophone(dimension, x, y, z, facing, ticks));
    }

    /** Whether Videophone {@code number} is loaded and powered, so it can be called and stays in its call. */
    public boolean videophoneWorking(int number) {
        Videophone phone = videophones.get(number);
        return phone != null && ticks - phone.heard() <= VIDEOPHONE_LOST_TICKS;
    }

    /** Where Videophone {@code number} stands while it works, or null. */
    public Videophone videophone(int number) {
        return videophoneWorking(number) ? videophones.get(number) : null;
    }

    /** Whether {@code player} stands in {@code videophone}'s room, where everyone is in its call (DESIGN.md 10.2). */
    private boolean inRoom(EntityPlayer player, Videophone videophone) {
        double room = tunables.videophoneRoom();
        return player.dimension == videophone.dimension()
            && player.getDistanceSq(videophone.centerX(), videophone.centerY(), videophone.centerZ()) <= room * room;
    }

    /**
     * Whether {@code player} may dial, answer or hang up at Videophone {@code number}'s screen, and is told to step
     * closer if not: its calls are its room's, though its screen stays open from further off.
     */
    private boolean atVideophone(EntityPlayerMP player, int number) {
        Videophone videophone = videophone(number);
        if (videophone == null) return false;
        if (inRoom(player, videophone)) return true;
        tell(player, "becom.videophone.step_closer", (int) tunables.videophoneRoom());
        return false;
    }

    /**
     * {@code player}, at Videophone {@code number}'s screen, dials {@code to}; or, while it's in a call and they're in
     * its room, adds {@code to} to the call (group calls).
     */
    public CallBook.DialResult dialVideophone(EntityPlayerMP player, int number, int to) {
        UUID id = player.getUniqueID();
        if (!videophoneWorking(number) || handsetOf.containsKey(id)) return CallBook.DialResult.INVALID;
        if (!atVideophone(player, number)) return CallBook.DialResult.INVALID;
        Call call = book.callOfPhone(number);
        if (call != null) return add(player, call, number, to);
        CallBook.DialResult result = book.dial(id, number, to, now(), () -> worldCheck(player, number, to));
        report(player, result);
        // Whoever stands around it is in the call from the start, ringing out included.
        if (result == CallBook.DialResult.RINGING) updateRoom(number, now());
        return result;
    }

    /** {@code player}, at Videophone {@code number}'s screen, answers the call ringing it. */
    public boolean answerVideophone(EntityPlayerMP player, int number) {
        UUID id = player.getUniqueID();
        if (!videophoneWorking(number) || !isRinging(number) || handsetOf.containsKey(id)) return false;
        if (!atVideophone(player, number)) return false;
        if (!hasVoice.test(id)) {
            tell(player, "becom.call.no_voice");
            return false;
        }
        if (!book.answer(id, number, now())) return false;
        updateRoom(number, now());
        return true;
    }

    /**
     * Someone in Videophone {@code number}'s call hangs it up at its screen for the whole room, who hear the click as
     * they leave the call; or someone in its room who could answer it turns a ringing call down. Its call is the
     * room's: anyone near it but kept out of it, in another call say, can't end it (DESIGN.md 12.2).
     */
    public void hangUpVideophone(EntityPlayerMP player, int number) {
        if (!atVideophone(player, number)) return;
        UUID id = player.getUniqueID();
        if (isRinging(number)) {
            if (book.callOf(id) == null && !handsetOf.containsKey(id)) book.reject(number);
            return;
        }
        Call call = book.callOfPhone(number);
        Call.Leg leg = call == null ? null : call.legOf(number);
        if (leg != null && leg.players()
            .contains(id)) book.hangUpPhone(number);
    }

    /**
     * Once a second: Videophones not heard from leave their calls (D11); the rest take in whoever stands around them
     * (DESIGN.md 10.2); and the players around each loudspeaker learn the level they hear the call at.
     */
    private void updateVideophones(long now) {
        for (int number : new ArrayList<>(videophones.keySet())) {
            if (!videophoneWorking(number)) {
                videophones.remove(number);
                book.phoneGone(number);
                continue;
            }
            Call call = book.callOfPhone(number);
            Call.Leg leg = call == null ? null : call.legOf(number);
            if (leg != null && leg.state() == Call.Leg.State.ACTIVE) updateRoom(number, now);
        }
        sendLoudspeakerLevels();
    }

    /**
     * Whoever stands around a Videophone is in its room, but nobody joins it without voice, who could neither hear nor
     * be heard, nor while their own phone rings, which they couldn't answer from a call. Someone already in it stays
     * through their voice's grace, as on any phone (DESIGN.md 10).
     */
    private void updateRoom(int number, long now) {
        Videophone phone = videophones.get(number);
        if (phone == null) return;
        Call call = book.callOfPhone(number);
        List<UUID> room = new ArrayList<>();
        for (UUID player : playersAround(phone, tunables.videophoneRoom())) {
            boolean inRoom = call != null && book.callOf(player) == call;
            if (inRoom || hasVoice.test(player) && !ownPhoneRinging(player)) room.add(player);
        }
        book.setRoom(number, room, now);
    }

    private boolean ownPhoneRinging(UUID player) {
        for (int number : carried.carriedBy(player)) if (isRinging(number)) return true;
        return false;
    }

    /**
     * The players within {@code range} of a Videophone, nearest first, so the nearest join a call that's nearly full.
     * Someone on a Telephone's handset is on that phone, not this one.
     */
    private List<UUID> playersAround(Videophone phone, double range) {
        double rangeSq = range * range;
        List<EntityPlayer> near = new ArrayList<>();
        for (EntityPlayer player : playersIn(phone.dimension())) {
            if (player.isDead || handsetOf.containsKey(player.getUniqueID())) continue;
            if (player.getDistanceSq(phone.centerX(), phone.centerY(), phone.centerZ()) <= rangeSq) near.add(player);
        }
        near.sort(Comparator.comparingDouble(p -> p.getDistanceSq(phone.centerX(), phone.centerY(), phone.centerZ())));
        List<UUID> ids = new ArrayList<>(near.size());
        for (EntityPlayer player : near) ids.add(player.getUniqueID());
        return ids;
    }

    // The self-test's fake players are in no world's player list; it adds them here to stand at a Videophone.
    private final List<EntityPlayer> selfTestPlayers = new ArrayList<>();

    private List<EntityPlayer> playersIn(int dimension) {
        List<EntityPlayer> players = new ArrayList<>();
        WorldServer world = DimensionManager.getWorld(dimension);
        if (world != null) {
            for (Object o : world.playerEntities) if (o instanceof EntityPlayer player) players.add(player);
        }
        for (EntityPlayer player : selfTestPlayers) {
            if (player.worldObj == world && !players.contains(player)) players.add(player);
        }
        return players;
    }

    /** Lets a self-test fake player stand at a Videophone, as if it were in its world's player list. */
    public void presentForSelfTest(EntityPlayer player) {
        if (!selfTestPlayers.contains(player)) selfTestPlayers.add(player);
    }

    /**
     * The players around a Videophone's loudspeaker who aren't in its call hear it too (DESIGN.md 10.2), at the level
     * the Videophone hears each speaker at, as around a Transceiver (DESIGN.md 13.5).
     */
    private void sendLoudspeakerLevels() {
        Map<Heard, SignalLevel> current = new HashMap<>();
        for (Call call : book.calls()) {
            if (call.state() != Call.State.CONNECTED) continue;
            List<UUID> inCall = call.participants();
            for (Call.Leg to : call.activeLegs()) {
                Videophone loud = kindOf(to.phone()) == PhoneKind.VIDEOPHONE ? videophone(to.phone()) : null;
                if (loud == null) continue;
                for (UUID listener : playersAround(loud, tunables.videophoneLoudspeaker())) {
                    if (inCall.contains(listener)) continue;
                    for (Call.Leg from : call.activeLegs()) {
                        if (from == to) continue;
                        SignalLevel level = call.level(from, to);
                        if (!level.atLeast(SignalLevel.LOW)) continue;
                        for (UUID speaker : from.players()) current.putIfAbsent(new Heard(listener, speaker), level);
                    }
                }
            }
        }
        for (Map.Entry<Heard, SignalLevel> entry : current.entrySet()) {
            if (entry.getValue() == loudspeakerLevels.get(entry.getKey())) continue;
            EntityPlayerMP mp = player(
                entry.getKey()
                    .listener());
            if (mp != null) BeComPackets.send(
                new LevelPacket(
                    entry.getKey()
                        .speaker(),
                    CallGroup.NAME,
                    entry.getValue()),
                mp);
        }
        for (Heard gone : loudspeakerLevels.keySet()) {
            if (current.containsKey(gone)) continue;
            // Someone who walked into the call has their level from it now; clearing it would silence them.
            Call theirs = book.callOf(gone.listener());
            if (theirs != null && theirs == book.callOf(gone.speaker())) continue;
            EntityPlayerMP mp = player(gone.listener());
            if (mp != null) BeComPackets.send(new LevelPacket(gone.speaker(), CallGroup.NAME, SignalLevel.NONE), mp);
        }
        loudspeakerLevels.clear();
        loudspeakerLevels.putAll(current);
    }

    // endregion

    // region CallBook.Listener

    @Override
    public void ringing(Call call, Call.Leg leg) {
        TileTelephone telephone = phones.telephone(leg.phone());
        if (telephone != null) telephone.setRinging(true);
        PhoneRecord record = phones.record(leg.phone());
        if (record == null || !record.kind()
            .carried()) return;
        ringingCarried.put(leg.phone(), ticks);
        ringCarried(leg.phone());
        // The notice in the corner (DESIGN.md 11.2): who calls, and from which number.
        UUID holder = carried.holderOf(leg.phone());
        Call.Leg ringer = call.legOf(leg.invitedBy());
        if (holder != null && ringer != null) {
            String name = ringer.player() != null ? nameOf(ringer.player(), phones.name(ringer.phone()))
                : phones.name(ringer.phone());
            RingPacket packet = RingPacket.ringing(name, ringer.phone());
            notices.put(leg.phone(), new Notice(leg.phone(), holder, packet, ++noticesShown));
            showNotice(holder);
        }
    }

    @Override
    public void connected(Call call, Call.Leg answered) {
        TileTelephone telephone = phones.telephone(answered.phone());
        if (telephone != null) telephone.setRinging(false);
        stopRinging(answered.phone());
        Set<Pair> pairs = talked.computeIfAbsent(call.id(), id -> new HashSet<>());
        for (Call.Leg other : call.activeLegs()) {
            if (other != answered) pairs.add(Pair.of(answered.phone(), other.phone()));
        }
        List<UUID> players = call.participants();
        for (UUID player : players) {
            GtnhVoiceApi.groupManager()
                .join(player, group);
            tone(player, TonePacket.Tone.NONE);
            showNotice(player);
        }
        completeQuests(call);
        // Levels now, not at the next whole second: until a direction has a level it gets no route, and "hello?" is
        // lost.
        book.evaluate(call, now(), quality);
        publish();
    }

    @Override
    public void legLeft(Call call, Call.Leg leg, Set<UUID> players, CallBook.EndReason reason) {
        TileTelephone telephone = phones.telephone(leg.phone());
        if (telephone != null) telephone.setRinging(false);
        stopRinging(leg.phone());
        // Its rows are written now, while everyone's names are still known; the rest when the call ends.
        for (Call.Leg other : call.legs()) if (other != leg) record(call, leg, other);
        phones.registry()
            .changed();
        List<UUID> stay = call.participants();
        for (UUID player : players) leaveCall(player, stay, reason);
        if (!leg.answered()) addFailed(call, leg, reason);
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
            stopRinging(leg.phone());
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

    /** The quest tasks a call completes as it connects or a phone joins it (DESIGN.md 15.5). */
    private void completeQuests(Call call) {
        CallGraph graph = network.graph();
        CallQuests.Done done = CallQuests.of(
            call,
            this::kindOf,
            (from, to) -> graph.hops(entriesOf(from), entriesOf(to))
                .contains(CallGraph.HopKind.ME_BRIDGE),
            this::dimensionOf);
        if (!done.firstCall()
            .isEmpty()) BeComQuests.complete(QuestTask.FIRST_CALL, done.firstCall());
        if (!done.mobileCall()
            .isEmpty()) BeComQuests.complete(QuestTask.MOBILE_CALL, done.mobileCall());
        if (!done.meBridgeCall()
            .isEmpty()) BeComQuests.complete(QuestTask.ME_BRIDGE_CALL, done.meBridgeCall());
        if (!done.betweenDimensions()
            .isEmpty()) BeComQuests.complete(QuestTask.CALL_BETWEEN_DIMENSIONS, done.betweenDimensions());
    }

    /** The dimension a player is in, or null when they aren't online. */
    private Integer dimensionOf(UUID id) {
        EntityPlayer player = playerOrPresent(id);
        return player == null ? null : player.dimension;
    }

    /** A phone added to a call stopped ringing unanswered: whoever added it hears why (DESIGN.md 11.2). */
    private void addFailed(Call call, Call.Leg leg, CallBook.EndReason reason) {
        Call.Leg inviter = call.legOf(leg.invitedBy());
        if (inviter == null) return;
        String key = switch (reason) {
            case NO_ANSWER -> "becom.call.add_no_answer";
            case REJECTED -> "becom.call.add_rejected";
            default -> "becom.call.add_lost";
        };
        String number = PhoneNumbers.format(leg.phone());
        for (UUID player : inviter.players()) tell(player, key, number);
    }

    /**
     * {@code player} is out of their call: out of the group, the others' levels cleared, and told why. A null
     * {@code reason} means they walked out of a speakerphone's room and the call goes on without them.
     */
    private void leaveCall(UUID player, List<UUID> others, CallBook.EndReason reason) {
        GtnhVoiceApi.groupManager()
            .leave(player, group);
        // Whoever still holds a handset hears the line go dead until they put it down. Any other phone clicks, which
        // also stops a ringback it played; someone walking out of a room just stops hearing a ringback.
        TonePacket.Tone tone = reason == null ? TonePacket.Tone.NONE
            : handsetOf.containsKey(player) ? TonePacket.Tone.BUSY : TonePacket.Tone.HANG_UP;
        tone(player, tone);
        if (reason == CallBook.EndReason.NO_ANSWER) tell(player, "becom.call.no_answer");
        if (reason == CallBook.EndReason.DROPPED) tell(player, "becom.call.dropped");
        // A phone of theirs that kept ringing through the call shows its notice again.
        showNotice(player);
        EntityPlayerMP mp = player(player);
        if (mp == null) return;
        for (UUID other : others) {
            if (!other.equals(player)) BeComPackets.send(new LevelPacket(other, CallGroup.NAME, SignalLevel.NONE), mp);
        }
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
            showNotice(player);
        }
        if (call.state() == Call.State.CONNECTED) {
            BeComQuests.complete(QuestTask.FIRST_CALL, new ArrayList<>(joined));
            if (CallQuests.spansDimensions(players, this::dimensionOf)) {
                BeComQuests.complete(QuestTask.CALL_BETWEEN_DIMENSIONS, new ArrayList<>(joined));
            }
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
        if (!toneStops.isEmpty()) {
            for (Map.Entry<UUID, Long> stop : new ArrayList<>(toneStops.entrySet())) {
                if (ticks >= stop.getValue()) tone(stop.getKey(), TonePacket.Tone.NONE);
            }
        }
        for (int number : new ArrayList<>(ringingCarried.keySet())) ringCarried(number);
        // A ringing phone that changed hands takes its notice along, and whoever got busy or free some other way,
        // dialling out say, sees the right notice by the next tick.
        if (!notices.isEmpty() || !shownNotices.isEmpty()) {
            Set<UUID> holders = new HashSet<>(shownNotices.keySet());
            for (Map.Entry<Integer, Notice> entry : notices.entrySet()) {
                Notice notice = entry.getValue();
                UUID carrier = carried.holderOf(notice.phone());
                if (carrier != null && !carrier.equals(notice.holder())) {
                    notice = new Notice(notice.phone(), carrier, notice.packet(), notice.order());
                    entry.setValue(notice);
                }
                holders.add(notice.holder());
            }
            for (UUID holder : holders) showNotice(holder);
        }
        if (ticks % CORD_CHECK_TICKS == 0) checkCords();
        if (ticks % TICKS_PER_SECOND == 0) {
            reachable.values()
                .removeIf(known -> ticks - known.tick() >= TICKS_PER_SECOND);
            updateVideophones(now);
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
            return width(speaker.phone(), listener.phone());
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
            // A self-test stand-in is checked where it stands, as an online player is.
            EntityPlayer player = playerOrPresent(entry.getValue());
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
        ringingCarried.clear();
        notices.clear();
        shownNotices.clear();
        reachable.clear();
        toneStops.clear();
        videophones.clear();
        loudspeakerLevels.clear();
        callBars.clear();
        selfTestPlayers.clear();
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
                    double enterBad = dropouts.enterBadChance(loss);
                    double leaveBad = dropouts.leaveBadChance();
                    // A Videophone plays the call from its loudspeaker to everyone around it, its room included
                    // (DESIGN.md 10.2, 13.3).
                    Videophone loud = kindOf(to.phone()) == PhoneKind.VIDEOPHONE ? videophone(to.phone()) : null;
                    for (UUID speaker : from.players()) {
                        if (loud != null) {
                            builder.loudspeaker(
                                speaker,
                                new CallSnapshot.Loudspeaker(
                                    to.phone(),
                                    loud.dimension(),
                                    loud.centerX(),
                                    loud.centerY(),
                                    loud.centerZ(),
                                    tunables.videophoneLoudspeaker(),
                                    enterBad,
                                    leaveBad));
                            continue;
                        }
                        for (UUID listener : to.players()) {
                            builder.route(speaker, new CallSnapshot.Route(listener, enterBad, leaveBad));
                        }
                    }
                }
            }
        }
        group.publish(builder.build());
        publishedVersion = book.version();
        sendCallBars();
    }

    /**
     * Tells each player in a connected call the weakest level they hear it at, for their signal bars, and those no
     * longer in one that they aren't. Someone in a Videophone's room hears the others in it directly, not through it.
     */
    private void sendCallBars() {
        Map<UUID, SignalLevel> current = new HashMap<>();
        for (Call call : book.calls()) {
            if (call.state() != Call.State.CONNECTED) continue;
            List<Call.Leg> active = call.activeLegs();
            for (Call.Leg own : active) {
                SignalLevel weakest = null;
                for (Call.Leg other : active) {
                    if (other == own) continue;
                    SignalLevel level = call.level(other, own);
                    weakest = weakest == null ? level : SignalLevel.min(weakest, level);
                }
                for (UUID player : own.players()) current.put(player, weakest == null ? SignalLevel.NONE : weakest);
            }
        }
        for (Map.Entry<UUID, SignalLevel> entry : current.entrySet()) {
            if (entry.getValue() == callBars.get(entry.getKey())) continue;
            EntityPlayerMP mp = player(entry.getKey());
            if (mp != null) BeComPackets.send(new CallBarsPacket(true, entry.getValue()), mp);
        }
        for (UUID gone : callBars.keySet()) {
            if (current.containsKey(gone)) continue;
            EntityPlayerMP mp = player(gone);
            if (mp != null) BeComPackets.send(new CallBarsPacket(false, SignalLevel.NONE), mp);
        }
        callBars.clear();
        callBars.putAll(current);
    }

    /** The bars {@code player} was last told their call shows, or null when they're in none; for the self-test. */
    public SignalLevel callBarsForSelfTest(UUID player) {
        return callBars.get(player);
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
        EntityPlayer online = playerOrPresent(id);
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

    /** {@code id}'s player: online, or one of the self-test's fake players standing in for one. */
    public EntityPlayer playerOrPresent(UUID id) {
        EntityPlayerMP online = player(id);
        if (online != null) return online;
        for (EntityPlayer player : selfTestPlayers) if (player.getUniqueID()
            .equals(id)) return player;
        return null;
    }

    /** Plays {@code tone} on {@code player}'s phone, in place of whatever it played. */
    private void tone(UUID player, TonePacket.Tone tone) {
        toneStops.remove(player);
        if (toneLog != null) toneLog.put(player, tone);
        EntityPlayerMP mp = player(player);
        if (mp != null) BeComPackets.send(new TonePacket(tone), mp);
    }

    private void tone(EntityPlayer player, TonePacket.Tone tone) {
        tone(player.getUniqueID(), tone);
    }

    /** A tone that loops until a handset goes down, played for a while on a phone with no handset (DESIGN.md 10). */
    private void briefTone(UUID player, TonePacket.Tone tone) {
        tone(player, tone);
        toneStops.put(player, ticks + BRIEF_TONE_TICKS);
    }

    /**
     * From now on, keeps the last tone each player was played, what they were told and whether a ringing notice shows
     * for them, for the self-test.
     */
    public void logForSelfTest() {
        if (toneLog == null) toneLog = new HashMap<>();
        if (messageLog == null) messageLog = new HashMap<>();
        if (noticeLog == null) noticeLog = new HashMap<>();
    }

    /** Whether a ringing notice shows for {@code player}, as far as the log since {@link #logForSelfTest} goes. */
    public boolean noticeForSelfTest(UUID player) {
        return noticeLog != null && noticeLog.getOrDefault(player, false);
    }

    /** The last tone {@code player} was played since {@link #logForSelfTest}, or null. */
    public TonePacket.Tone lastToneForSelfTest(UUID player) {
        return toneLog == null ? null : toneLog.get(player);
    }

    /** The language key of the last thing {@code player} was told since {@link #logForSelfTest}, or null. */
    public String lastMessageForSelfTest(UUID player) {
        return messageLog == null ? null : messageLog.get(player);
    }

    private void tell(EntityPlayerMP player, String key, Object... args) {
        if (messageLog != null) messageLog.put(player.getUniqueID(), key);
        player.addChatComponentMessage(new ChatComponentTranslation(key, args));
    }

    private void tell(UUID player, String key, Object... args) {
        if (messageLog != null) messageLog.put(player, key);
        EntityPlayerMP mp = player(player);
        if (mp != null) mp.addChatComponentMessage(new ChatComponentTranslation(key, args));
    }
}
