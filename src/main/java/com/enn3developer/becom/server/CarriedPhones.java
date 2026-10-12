package com.enn3developer.becom.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;

import com.enn3developer.becom.item.Carried;
import com.enn3developer.becom.item.ItemCarriedPhone;
import com.enn3developer.becom.logic.LevelScale;
import com.enn3developer.becom.logic.SignalLevel;
import com.enn3developer.becom.logic.Tunables;
import com.enn3developer.becom.logic.net.CallGraph;
import com.enn3developer.becom.logic.phone.PhoneKind;
import com.enn3developer.becom.logic.phone.PhoneRecord;
import com.enn3developer.becom.logic.radio.AntennaNetwork;
import com.enn3developer.becom.logic.radio.RadioHops;
import com.enn3developer.becom.packet.BeComPackets;
import com.enn3developer.becom.packet.PhoneStatusPacket;

/**
 * Phones players carry (DESIGN.md 8, 8.2, D14, D36): which player carries which number, the service each working one
 * has where its holder stands, and their batteries. Each player is looked at once a second, spread over the ticks
 * (DESIGN.md 6.5). Server thread only.
 * <p>
 * A carried phone gets its number the first time the server sees it, and its first user owns it (DESIGN.md 12.1).
 * The same number in two places at once is a copy, made in creative or with a world editor: the one found second gets
 * a number of its own with the original's owner and settings, as a copied Telephone does (X17).
 */
public final class CarriedPhones {

    private static final int TICKS_PER_SECOND = 20;

    /** What the calls need to hear about carried phones. */
    public interface Calls {

        /** Whether {@code number} is in a call, answered or dialling: it drains the in-call rate. */
        boolean inCall(int number);

        /** {@code number} has no service any more, or isn't carried: switched off, flat, dropped or stored. */
        void lost(int number);
    }

    /** A working phone's service: the antennas (or other ways in) it reaches and the SNR to each, and its level. */
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
    private long ticks;

    public CarriedPhones(MinecraftServer server, Tunables tunables, PhoneService phones, AntennaNetwork antennas,
        Function<EntityPlayer, RadioHops.End> endOf) {
        this.server = server;
        this.tunables = tunables;
        this.levels = tunables.levelScale();
        this.phones = phones;
        // The Mobile Phone: every antenna that reaches its holder on UHF (DESIGN.md 8.2), rain included (D26).
        access.put(PhoneKind.MOBILE, (holder, phone) -> {
            List<CallGraph.Entry> entries = new ArrayList<>();
            boolean rains = BeComServer
                .rains(holder.worldObj, (int) Math.floor(holder.posX), (int) Math.floor(holder.posZ));
            for (AntennaNetwork.Best reached : antennas
                .reach(holder.dimension, endOf.apply(holder), holder.getUniqueID(), antennas.uhf(), rains)) {
                entries.add(
                    new CallGraph.Entry(
                        new CallGraph.NodeVertex(
                            holder.dimension,
                            reached.antenna()
                                .ref()
                                .pos()),
                        reached.losses()
                            .snr()));
            }
            return entries;
        });
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

    /** Every carried phone working now: carried, switched on and charged, with or without an antenna in reach. */
    public Set<Integer> working() {
        return Collections.unmodifiableSet(service.keySet());
    }

    /** The stack of {@code number} in its holder's inventory or bauble slots, or null. */
    public ItemStack stackOf(int number) {
        EntityPlayerMP holder = player(holders.get(number));
        return holder == null ? null : stackOf(holder, number);
    }

    private static ItemStack stackOf(EntityPlayer player, int number) {
        for (Carried.Slot slot : Carried.slots(player)) {
            ItemStack stack = slot.stack();
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
        for (Carried.Slot slot : Carried.slots(player)) {
            ItemStack stack = slot.stack();
            if (!ItemCarriedPhone.is(stack) || ItemCarriedPhone.isTooNew(stack)) continue;
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
                SignalLevel cap = record == null ? SignalLevel.NONE
                    : record.kind()
                        .cap();
                level = SignalLevel
                    .min(levels.next(previous.level(), new Service(entries, SignalLevel.NONE).best()), cap);
            }
            service.put(number, new Service(List.copyOf(entries), level));
        }
        // The bars by the hotbar show the first phone they carry (DESIGN.md 11.2).
        SignalLevel bars = found.isEmpty() ? SignalLevel.NONE : serviceOf(found.get(0)).level();
        if (shown.put(id, bars) != bars) BeComPackets.send(new PhoneStatusPacket(bars), player);
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
     * Phones drain their charge while switched on, more in a call, and switch off when it's flat (D36, DESIGN.md
     * 11.3). Once a second, the second's worth.
     */
    private void drain(List<EntityPlayerMP> players) {
        for (EntityPlayerMP player : players) {
            for (Carried.Slot slot : Carried.slots(player)) {
                ItemStack stack = slot.stack();
                if (!ItemCarriedPhone.is(stack) || ItemCarriedPhone.isTooNew(stack) || !ItemCarriedPhone.on(stack))
                    continue;
                int number = ItemCarriedPhone.number(stack);
                boolean talking = number >= 0 && calls != null && calls.inCall(number);
                double wanted = (double) (talking ? tunables.phoneCallDrain() : tunables.phoneStandbyDrain())
                    * TICKS_PER_SECOND;
                if (wanted <= 0) continue;
                ItemCarriedPhone item = (ItemCarriedPhone) stack.getItem();
                if (item.drain(stack, wanted) < wanted) ItemCarriedPhone.setOn(stack, false);
            }
        }
    }

    public void stop() {
        holders.clear();
        carried.clear();
        service.clear();
        shown.clear();
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

    /** For the self-test: looks at a fake player's phones now, and drains a second's worth. */
    public void refreshForSelfTest(EntityPlayerMP player) {
        refresh(player);
    }

    public void drainForSelfTest(EntityPlayerMP player) {
        drain(List.of(player));
    }
}
