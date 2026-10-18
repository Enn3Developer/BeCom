package com.enn3developer.becom.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.S29PacketSoundEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

import com.enn3developer.becom.item.Carried;
import com.enn3developer.becom.item.ItemCarriedPhone;
import com.enn3developer.becom.logic.LevelScale;
import com.enn3developer.becom.logic.SignalLevel;
import com.enn3developer.becom.logic.Tunables;
import com.enn3developer.becom.logic.net.CallGraph;
import com.enn3developer.becom.logic.phone.BatteryUse;
import com.enn3developer.becom.logic.phone.PhoneKind;
import com.enn3developer.becom.logic.phone.PhoneRecord;
import com.enn3developer.becom.logic.radio.AntennaNetwork;
import com.enn3developer.becom.logic.radio.RadioHops;
import com.enn3developer.becom.packet.BeComPackets;
import com.enn3developer.becom.packet.PhoneStatusPacket;

/**
 * Phones players carry: which player carries which number, the service each working one
 * has where its holder stands, and their batteries. Each player is looked at once a second, spread over the ticks.
 * Server thread only.
 * <p>
 * A carried phone gets its number the first time the server sees it, and its first user owns it.
 * The same number in two places at once is a copy, made in creative or with a world editor: the one found second gets
 * a number of its own with the original's owner and settings, as a copied Telephone does.
 */
public final class CarriedPhones {

    private static final int TICKS_PER_SECOND = 20;
    private static final long TICKS_PER_DAY = 24_000;

    /** What the calls need to hear about carried phones. */
    public interface Calls {

        /** Whether {@code number} is in a call, answered or dialling: it drains the in-call rate. */
        boolean inCall(int number);

        /** {@code number} has no service any more, or isn't carried: switched off, flat, dropped or stored. */
        void lost(int number);
    }

    /**
     * A working phone's service: the antennas, satellites or other ways in it reaches and the SNR to each, and its
     * level.
     */
    public record Service(List<CallGraph.Entry> entries, SignalLevel level) {

        public static final Service NONE = new Service(List.of(), SignalLevel.NONE);

        public double best() {
            double best = Double.NEGATIVE_INFINITY;
            for (CallGraph.Entry entry : entries) best = Math.max(best, entry.snr());
            return best;
        }
    }

    /** Where a kind of phone gets its service. */
    public interface Access {

        List<CallGraph.Entry> entries(EntityPlayerMP holder, ItemStack phone);
    }

    private final MinecraftServer server;
    private final Tunables tunables;
    private final LevelScale levels;
    private final PhoneService phones;
    private final Map<PhoneKind, Access> access = new HashMap<>();
    private Calls calls;
    private final Map<Integer, UUID> holders = new HashMap<>();
    private final Map<UUID, List<Integer>> carried = new HashMap<>();
    private final Map<Integer, Service> service = new HashMap<>();
    // The service level each player's client last heard of, for their signal bars.
    private final Map<UUID, SignalLevel> shown = new HashMap<>();
    // Each carried phone's charge at the last second, which phones gained charge since, and whose saver is on.
    private final Map<Integer, Double> lastCharge = new HashMap<>();
    private final Set<Integer> charging = new HashSet<>();
    private final Set<Integer> savers = new HashSet<>();
    // Phones whose screen is open now.
    private final Set<Integer> screens = new HashSet<>();
    private Predicate<UUID> watching = id -> false;
    private java.util.function.IntPredicate appsRunning = n -> false;
    private java.util.function.BiConsumer<Integer, Long> appsBilled = (n, eu) -> {};
    private final AntennaNetwork antennas;
    private final SpaceService space;
    private long ticks;

    /** @param endOf where a holder hears and talks from, or null when they have no position */
    public CarriedPhones(MinecraftServer server, Tunables tunables, PhoneService phones, AntennaNetwork antennas,
        SpaceService space, Function<EntityPlayer, RadioHops.End> endOf) {
        this.server = server;
        this.tunables = tunables;
        this.levels = tunables.levelScale();
        this.phones = phones;
        // The Mobile Phone: every antenna that reaches its holder on UHF, and the satellites over
        // their dimension (9.1), rain included.
        this.antennas = antennas;
        this.space = space;
        access.put(PhoneKind.MOBILE, (holder, phone) -> {
            // A holder whose position isn't a number has none, and so no service.
            RadioHops.End end = endOf.apply(holder);
            if (end == null) return List.of();
            return entriesAt(holder.worldObj, end, holder.getUniqueID());
        });
    }

    /**
     * The ways in a radio at {@code end} reaches: every antenna that reaches it on UHF, and the
     * satellites over its dimension (9.1), rain included. {@code user} is who uses it, for team-locked antennas.
     */
    public List<CallGraph.Entry> entriesAt(World world, RadioHops.End end, UUID user) {
        int dimension = world.provider.dimensionId;
        List<CallGraph.Entry> entries = new ArrayList<>();
        boolean rains = BeComServer.rains(world, (int) Math.floor(end.x()), (int) Math.floor(end.z()));
        double sky = space.hop(dimension, end.depth(), antennas.uhf(), rains ? space.weather(dimension) : 0);
        if (sky > Double.NEGATIVE_INFINITY) entries.add(new CallGraph.Entry(new CallGraph.SkyVertex(dimension), sky));
        for (AntennaNetwork.Best reached : antennas.reach(dimension, end, user, antennas.uhf(), rains)) {
            entries.add(
                new CallGraph.Entry(
                    new CallGraph.NodeVertex(
                        dimension,
                        reached.antenna()
                            .ref()
                            .pos()),
                    reached.losses()
                        .snr()));
        }
        return entries;
    }

    /** The level a set of ways in gives, by its best one. */
    public SignalLevel levelOf(List<CallGraph.Entry> entries) {
        return entries.isEmpty() ? SignalLevel.NONE : levels.levelOf(new Service(entries, SignalLevel.NONE).best());
    }

    /** Where phones of {@code kind} get service, or null for a kind with none set. */
    public Access accessOf(PhoneKind kind) {
        return access.get(kind);
    }

    /** Where phones of {@code kind} get service; the ME Phone's comes from its ME network. */
    public void setAccess(PhoneKind kind, Access way) {
        access.put(kind, way);
    }

    public void setCalls(Calls calls) {
        this.calls = calls;
    }

    // region Queries

    /** Who carries {@code number}, or null when nobody online does. */
    public UUID holderOf(int number) {
        return holders.get(number);
    }

    /** The phones {@code player} carries, working or not, in the order a phone is chosen. */
    public List<Integer> carriedBy(UUID player) {
        List<Integer> numbers = carried.get(player);
        return numbers == null ? List.of() : Collections.unmodifiableList(numbers);
    }

    /** The service {@code number} has: none when it isn't carried, switched on and charged. */
    public Service serviceOf(int number) {
        Service found = service.get(number);
        return found == null ? Service.NONE : found;
    }

    /**
     * The best level a call through a phone of {@code kind} with this service reaches: an ME Phone reaches Max only
     * through its ME network, and out of its range is a Mobile Phone.
     */
    public static SignalLevel cap(PhoneKind kind, List<CallGraph.Entry> entries) {
        if (kind != PhoneKind.ME_PHONE) return kind.cap();
        for (CallGraph.Entry entry : entries) if (entry.vertex() instanceof CallGraph.MeVertex) return kind.cap();
        return SignalLevel.min(kind.cap(), PhoneKind.MOBILE.cap());
    }

    /** Every carried phone working now: carried, switched on and charged, with or without an antenna in reach. */
    public Set<Integer> working() {
        return Collections.unmodifiableSet(service.keySet());
    }

    /** The stack of {@code number} in its holder's inventory or bauble slots, or on their cursor; or null. */
    public ItemStack stackOf(int number) {
        EntityPlayerMP holder = player(holders.get(number));
        return holder == null ? null : stackOf(holder, number);
    }

    private static ItemStack stackOf(EntityPlayer player, int number) {
        for (ItemStack stack : Carried.stacks(player)) {
            if (ItemCarriedPhone.is(stack) && ItemCarriedPhone.number(stack) == number) return stack;
        }
        return null;
    }

    // endregion

    // region Ticking

    public void tick() {
        ticks++;
        List<EntityPlayerMP> players = players();
        int slot = (int) (ticks % TICKS_PER_SECOND);
        for (int i = slot; i < players.size(); i += TICKS_PER_SECOND) refresh(players.get(i));
        if (ticks % TICKS_PER_SECOND == 0) drain(players);
    }

    /** Looks at one player's phones again: numbers, copies, and the service each has where they stand. */
    public void refresh(EntityPlayerMP player) {
        UUID id = player.getUniqueID();
        List<Integer> found = new ArrayList<>();
        Map<Integer, ItemStack> stacks = new HashMap<>();
        // The cursor counts: a phone being moved to another slot mustn't drop out of its call.
        for (ItemStack stack : Carried.stacks(player)) {
            if (!ItemCarriedPhone.is(stack)) continue;
            ItemCarriedPhone item = (ItemCarriedPhone) stack.getItem();
            int number = ItemCarriedPhone.number(stack);
            PhoneRecord record = number < 0 ? null : phones.record(number);
            if (record == null || record.kind() != item.kind()) {
                // Its first use, or a number this world never gave out (a phone from another world): a fresh one.
                record = phones.registerCarried(item.kind(), player);
                ItemCarriedPhone.setNumber(stack, record.number());
            } else if (found.contains(number) || carriedByAnother(number, id)) {
                record = phones.registerCarriedCopy(record);
                ItemCarriedPhone.setNumber(stack, record.number());
            }
            found.add(record.number());
            stacks.put(record.number(), stack);
            holders.put(record.number(), id);
        }
        List<Integer> before = carried.put(id, found);
        if (before != null) {
            for (int number : before) if (!found.contains(number)) forget(number, id);
        }
        for (int number : found) {
            ItemStack stack = stacks.get(number);
            Access way = access.get(((ItemCarriedPhone) stack.getItem()).kind());
            if (!ItemCarriedPhone.working(stack) || way == null) {
                if (service.remove(number) != null || calls != null && calls.inCall(number)) lost(number);
                continue;
            }
            List<CallGraph.Entry> entries = way.entries(player, stack);
            Service previous = serviceOf(number);
            SignalLevel level = SignalLevel.NONE;
            if (!entries.isEmpty()) {
                PhoneRecord record = phones.record(number);
                SignalLevel cap = record == null ? SignalLevel.NONE : cap(record.kind(), entries);
                level = SignalLevel
                    .min(levels.next(previous.level(), new Service(entries, SignalLevel.NONE).best()), cap);
            }
            service.put(number, new Service(List.copyOf(entries), level));
        }
        // The bars by the hotbar show the first phone they carry.
        SignalLevel bars = found.isEmpty() ? SignalLevel.NONE : serviceOf(found.get(0)).level();
        if (shown.put(id, bars) != bars) BeComPackets.send(new PhoneStatusPacket(bars), player);
    }

    /**
     * The number of the phone {@code player} uses, after looking at their phones again unless this server already knows
     * it in their hands: a phone used for the first time gets its number, and a copy of someone else's its own,
     * before a screen shows anything of it.
     */
    public int numberFor(EntityPlayerMP player, ItemStack stack) {
        if (!ItemCarriedPhone.is(stack)) return -1;
        int number = ItemCarriedPhone.number(stack);
        PhoneRecord record = number < 0 ? null : phones.record(number);
        boolean known = record != null && record.kind() == ((ItemCarriedPhone) stack.getItem()).kind()
            && carriedBy(player.getUniqueID()).contains(number);
        if (!known) refresh(player);
        return ItemCarriedPhone.number(stack);
    }

    /** Whether another online player carries {@code number} right now: then this one is a copy. */
    private boolean carriedByAnother(int number, UUID player) {
        UUID other = holders.get(number);
        if (other == null || other.equals(player)) return false;
        EntityPlayerMP holder = player(other);
        return holder != null && stackOf(holder, number) != null;
    }

    private void forget(int number, UUID player) {
        if (!player.equals(holders.get(number))) return;
        holders.remove(number);
        service.remove(number);
        lost(number);
    }

    private void lost(int number) {
        if (calls != null) calls.lost(number);
    }

    /** The player logged out: their phones go with them. */
    public void logout(UUID player) {
        shown.remove(player);
        List<Integer> numbers = carried.remove(player);
        if (numbers != null) for (int number : numbers) forget(number, player);
    }

    /**
     * Phones drain their charge while switched on, more in a call, more with video and with their screen open, and
     * switch off when it's flat. Once a second, the second's worth, kept by what used it. A drop
     * past 20% or 5% warns the holder; a charge that went up since the last second is charging.
     */
    private void drain(List<EntityPlayerMP> players) {
        long day = server.worldServers.length == 0 ? 0 : server.worldServers[0].getWorldTime() / TICKS_PER_DAY;
        Set<Integer> seen = new HashSet<>();
        for (EntityPlayerMP player : players) {
            for (ItemStack stack : Carried.stacks(player)) {
                if (!ItemCarriedPhone.is(stack)) continue;
                ItemCarriedPhone item = (ItemCarriedPhone) stack.getItem();
                int number = ItemCarriedPhone.number(stack);
                double max = item.maxCharge(stack);
                double before = item.charge(stack);
                if (number >= 0) {
                    seen.add(number);
                    Double last = lastCharge.put(number, before);
                    if (last != null && before > last) charging.add(number);
                    else if (last != null && before < last) charging.remove(number);
                }
                if (!ItemCarriedPhone.on(stack)) continue;
                boolean saving = BatteryUse
                    .saving(ItemCarriedPhone.saver(stack), before, max, tunables.phoneSaverPercent());
                if (number >= 0) {
                    if (saving) savers.add(number);
                    else savers.remove(number);
                }
                boolean talking = number >= 0 && calls != null && calls.inCall(number);
                EnumMap<BatteryUse.Use, Integer> rates = new EnumMap<>(BatteryUse.Use.class);
                rates.put(BatteryUse.Use.STANDBY, tunables.phoneStandbyDrain());
                if (talking) {
                    rates.put(
                        BatteryUse.Use.CALLS,
                        Math.max(0, tunables.phoneCallDrain() - tunables.phoneStandbyDrain()));
                }
                if (talking && !saving && watching.test(player.getUniqueID())) {
                    rates.put(BatteryUse.Use.VIDEO, tunables.phoneVideoDrain());
                }
                if (number >= 0 && screens.contains(number))
                    rates.put(BatteryUse.Use.SCREEN, tunables.phoneScreenDrain());
                if (number >= 0 && appsRunning.test(number)) rates.put(BatteryUse.Use.APPS, tunables.phoneAppDrain());
                double wanted = 0;
                for (int rate : rates.values()) wanted += (double) rate * TICKS_PER_SECOND;
                if (wanted <= 0) continue;
                double took = item.drain(stack, wanted);
                BatteryUse use = ItemCarriedPhone.use(stack);
                for (Map.Entry<BatteryUse.Use, Integer> entry : rates.entrySet()) {
                    long eu = Math.round(took * entry.getValue() * TICKS_PER_SECOND / wanted);
                    use.add(day, entry.getKey(), eu);
                    if (entry.getKey() == BatteryUse.Use.APPS) appsBilled.accept(number, eu);
                }
                ItemCarriedPhone.setUse(stack, use);
                double after = before - took;
                if (number >= 0) lastCharge.put(number, after);
                if (took < wanted) ItemCarriedPhone.setOn(stack, false);
                int mark = BatteryUse.crossed(before, after, max);
                if (mark >= 0 && ItemCarriedPhone.warns(stack)) warn(player, mark);
            }
        }
        lastCharge.keySet()
            .retainAll(seen);
        charging.retainAll(seen);
        savers.retainAll(seen);
    }

    /** The low-battery warning: on screen, and a beep only its holder hears. */
    private static void warn(EntityPlayerMP player, int percent) {
        player.addChatComponentMessage(new ChatComponentTranslation("becom.phone.battery.low", percent));
        player.playerNetServerHandler
            .sendPacket(new S29PacketSoundEffect("note.pling", player.posX, player.posY, player.posZ, 0.6f, 1.6f));
    }

    /** Whether the phone's charge went up over the last second: on a charger, a battery buffer or a worn batpack. */
    public boolean charging(int number) {
        return charging.contains(number);
    }

    /** Whether the phone's battery saver is on, by hand or by itself: no video, and later no background apps. */
    public boolean saving(int number) {
        return savers.contains(number);
    }

    /** Whether the phone's screen is open, which costs it charge. */
    public void screenOpen(int number, boolean open) {
        if (number < 0) return;
        if (open) screens.add(number);
        else screens.remove(number);
    }

    /** Which phones run their apps now, which costs them charge, and who to tell what each second of it cost. */
    public void setApps(java.util.function.IntPredicate running, java.util.function.BiConsumer<Integer, Long> billed) {
        this.appsRunning = running;
        this.appsBilled = billed;
    }

    /** Who sees video right now, which costs their phone charge. */
    public void setWatching(Predicate<UUID> watching) {
        this.watching = watching;
    }

    public void stop() {
        holders.clear();
        carried.clear();
        service.clear();
        shown.clear();
        lastCharge.clear();
        charging.clear();
        savers.clear();
        screens.clear();
    }

    // endregion

    private List<EntityPlayerMP> players() {
        List<EntityPlayerMP> players = new ArrayList<>();
        for (Object o : server.getConfigurationManager().playerEntityList) {
            if (o instanceof EntityPlayerMP mp) players.add(mp);
        }
        return players;
    }

    private EntityPlayerMP player(UUID id) {
        if (id == null) return null;
        for (Object o : server.getConfigurationManager().playerEntityList) {
            if (o instanceof EntityPlayerMP mp && mp.getUniqueID()
                .equals(id)) return mp;
        }
        return null;
    }
}
