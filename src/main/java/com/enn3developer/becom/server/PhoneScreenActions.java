package com.enn3developer.becom.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;

import com.enn3developer.becom.gui.PhoneView;
import com.enn3developer.becom.item.ItemCarriedPhone;
import com.enn3developer.becom.logic.PhoneNumbers;
import com.enn3developer.becom.logic.RateLimiter;
import com.enn3developer.becom.logic.SignalLevel;
import com.enn3developer.becom.logic.call.Call;
import com.enn3developer.becom.logic.phone.PhoneKind;
import com.enn3developer.becom.logic.phone.PhoneRecord;
import com.enn3developer.becom.logic.phone.PhoneRegistry;
import com.enn3developer.becom.logic.video.VideoPlan;

/**
 * What the phone screen may do, checked on the server (DESIGN.md 14: client input). Every action re-checks that the
 * player can use the phone, at a Telephone or a Videophone or carrying the phone, and is allowed to do it, whatever
 * their client sent.
 */
public final class PhoneScreenActions {

    private PhoneScreenActions() {}

    public static PhoneView view(EntityPlayer player, PhoneHandle handle) {
        BeComServer becom = BeComServer.get();
        int number = handle.number();
        if (becom == null || number < 0) return PhoneView.EMPTY;
        PhoneRecord record = becom.phones.record(number);
        if (record == null) return PhoneView.EMPTY;
        boolean manage = Ownership.mayManage(player, record.owner());
        Call call = becom.calls.book()
            .callOfPhone(number);
        Call.Leg own = call == null ? null : call.legOf(number);
        PhoneView.State state;
        List<PhoneView.Member> members = new ArrayList<>();
        if (own != null) {
            if (own.state() == Call.Leg.State.RINGING) state = PhoneView.State.RINGING_IN;
            else if (call.state() == Call.State.RINGING) state = PhoneView.State.RINGING_OUT;
            else state = PhoneView.State.CONNECTED;
            for (Call.Leg leg : call.legs()) {
                if (leg == own || leg.state() == Call.Leg.State.LEFT) continue;
                boolean talking = own.state() == Call.Leg.State.ACTIVE && leg.state() == Call.Leg.State.ACTIVE;
                members.add(
                    new PhoneView.Member(
                        leg.phone(),
                        nameOn(becom, leg),
                        talking ? call.level(leg, own) : SignalLevel.NONE,
                        leg.state() == Call.Leg.State.RINGING));
            }
        } else if (handle instanceof PhoneHandle.Telephone) {
            UUID holder = becom.calls.holderOf(number);
            state = holder == null ? PhoneView.State.IDLE
                : holder.equals(player.getUniqueID()) ? PhoneView.State.OFF_HOOK : PhoneView.State.IN_USE;
        } else {
            state = PhoneView.State.IDLE;
        }
        PhoneView.Member first = members.isEmpty() ? null : members.get(0);
        List<PhoneView.Contact> contacts = new ArrayList<>();
        for (PhoneRegistry.Contact contact : becom.phones.registry()
            .contacts(player.getUniqueID(), number, becom.calls.reachableFrom(number))) {
            contacts.add(new PhoneView.Contact(contact.number(), contact.ownerName()));
        }
        // Calls are private to the people in them (DESIGN.md 12.2): a phone's history is for its owner and their team.
        List<PhoneView.Recent> recent = new ArrayList<>();
        if (manage) {
            for (PhoneRecord.HistoryEntry entry : record.history()) {
                recent
                    .add(new PhoneView.Recent(entry.time(), entry.otherNumber(), entry.otherName(), entry.direction()));
            }
        }
        List<PhoneView.Blocked> blocked = new ArrayList<>();
        if (manage) {
            for (Map.Entry<UUID, String> entry : record.blocked()
                .entrySet()) blocked.add(new PhoneView.Blocked(entry.getKey(), entry.getValue()));
        }
        boolean carried = handle instanceof PhoneHandle.CarriedPhone;
        ItemStack stack = handle instanceof PhoneHandle.CarriedPhone phone ? phone.stack(player) : null;
        boolean offline = handle instanceof PhoneHandle.Videophone && !becom.calls.videophoneWorking(number);
        return new PhoneView(
            number,
            record.ownerName(),
            manage,
            state,
            first == null ? "" : first.name(),
            first == null ? -1 : first.number(),
            first == null ? SignalLevel.NONE : first.level(),
            record.doNotDisturb(),
            record.hidden(),
            contacts,
            recent,
            blocked,
            carried,
            stack != null && ItemCarriedPhone.on(stack),
            record.silent(),
            carried ? becom.carried.serviceOf(number)
                .level() : SignalLevel.NONE,
            members,
            offline,
            videoOf(becom, player, record, call, own));
    }

    /** The video part of the screen: the player's own switches, on a phone with a camera (DESIGN.md 10.1, 11.2). */
    private static PhoneView.Video videoOf(BeComServer becom, EntityPlayer player, PhoneRecord record, Call call,
        Call.Leg own) {
        if (!record.kind()
            .video()) return PhoneView.Video.NONE;
        UUID id = player.getUniqueID();
        VideoPlan.Cameras cameras = becom.video.camerasOf(id);
        // The player's own leg, when they're in the call on this phone; at a Videophone, when they're in its room.
        boolean onIt = own != null && own.state() == Call.Leg.State.ACTIVE
            && own.players()
                .contains(id);
        boolean rearPossible = onIt && record.kind()
            .carried()
            && call.state() == Call.State.CONNECTED
            && call.activeLegs()
                .size() == 2
            && call.participants()
                .size() == 2;
        boolean active = onIt && !becom.video.plan()
            .tilesOf(id)
            .isEmpty();
        return new PhoneView.Video(true, cameras.front(), cameras.rear(), rearPossible, active);
    }

    /**
     * Who the screen names on a phone of the call: the player on it once there is one (the caller, or whoever
     * answered), as the call history does; the phone's owner while it still rings.
     */
    private static String nameOn(BeComServer becom, Call.Leg leg) {
        String owner = becom.phones.name(leg.phone());
        return leg.player() != null ? becom.calls.nameOf(leg.player(), owner) : owner;
    }

    /**
     * Dials {@code target}: from a Telephone, taking its handset if needed; from a carried phone or a Videophone, or
     * adds it to the call.
     */
    public static void dial(EntityPlayer player, PhoneHandle handle, int target) {
        BeComServer becom = BeComServer.get();
        if (becom == null || !(player instanceof EntityPlayerMP mp) || !handle.usableBy(player)) return;
        if (!PhoneNumbers.isValid(target)) return;
        if (handle instanceof PhoneHandle.Videophone) {
            becom.calls.dialVideophone(mp, handle.number(), target);
            return;
        }
        if (handle instanceof PhoneHandle.Telephone telephone) {
            Integer held = becom.calls.handsetOf(player.getUniqueID());
            if (held == null) {
                if (becom.calls.takeHandset(mp, telephone.tile()) != CallService.Pickup.TAKEN) return;
            } else if (held != handle.number()) {
                return;
            }
            becom.calls.dial(mp, handle.number(), target);
            return;
        }
        becom.calls.dialCarried(mp, handle.number(), target);
    }

    /** Picks up: answers a ringing phone, or takes a Telephone's handset to dial. */
    public static void pickUp(EntityPlayer player, PhoneHandle handle) {
        BeComServer becom = BeComServer.get();
        if (becom == null || !(player instanceof EntityPlayerMP mp) || !handle.usableBy(player)) return;
        if (handle instanceof PhoneHandle.Videophone) {
            becom.calls.answerVideophone(mp, handle.number());
            return;
        }
        if (handle instanceof PhoneHandle.Telephone telephone) {
            becom.calls.takeHandset(mp, telephone.tile());
            return;
        }
        becom.calls.answerCarried(mp, handle.number());
    }

    /**
     * Hangs up: puts a Telephone's handset back, ends the carried phone's or the Videophone's call, or turns down a
     * ringing one.
     */
    public static void hangUp(EntityPlayer player, PhoneHandle handle) {
        BeComServer becom = BeComServer.get();
        if (becom == null || !(player instanceof EntityPlayerMP mp) || !handle.usableBy(player)) return;
        if (handle instanceof PhoneHandle.Videophone) {
            becom.calls.hangUpVideophone(mp, handle.number());
            return;
        }
        if (handle instanceof PhoneHandle.Telephone) {
            Integer held = becom.calls.handsetOf(player.getUniqueID());
            if (held != null && held == handle.number()) becom.calls.hangUpHandset(player.getUniqueID());
            return;
        }
        if (becom.calls.isRinging(handle.number())) {
            becom.calls.rejectCarried(player, handle.number());
            return;
        }
        Call call = becom.calls.book()
            .callOf(player.getUniqueID());
        Call.Leg own = call == null ? null : call.legOf(player.getUniqueID());
        if (own != null && own.phone() == handle.number()) becom.calls.hangUp(mp);
    }

    /**
     * Switches a carried phone on or off (DESIGN.md 11.3): whoever carries it may, as with a walkie. It looks at the
     * player's phones again at once, so it shares the settings' rate limit.
     */
    public static void setPower(EntityPlayer player, PhoneHandle handle, boolean on) {
        BeComServer becom = BeComServer.get();
        if (becom == null || !(player instanceof EntityPlayerMP mp)
            || !(handle instanceof PhoneHandle.CarriedPhone phone)) return;
        ItemStack stack = phone.stack(player);
        if (stack == null || !mayChange(player)) return;
        ItemCarriedPhone.setOn(stack, on);
        player.inventory.markDirty();
        // Now, not at the next second: a phone switched off can't ring or stay in its call (DESIGN.md 11.3).
        becom.carried.refresh(mp);
    }

    // Settings and block-list changes are cheap one by one, but each recomputes contacts or the view: a client that
    // floods them only wastes the server's time (DESIGN.md 14: client input). A person never needs more than this.
    private static final int SETTINGS_CHANGES_PER_MINUTE = 60;
    private static final RateLimiter<UUID> SETTINGS_CHANGES = new RateLimiter<>(SETTINGS_CHANGES_PER_MINUTE, 60_000);
    // Each phone screen opened finds every phone in reach for its contacts; nobody opens one faster than this.
    private static final int SCREEN_OPENS_PER_SECOND = 4;
    private static final RateLimiter<UUID> SCREEN_OPENS = new RateLimiter<>(SCREEN_OPENS_PER_SECOND, 1000);

    private static boolean mayChange(EntityPlayer player) {
        long now = System.nanoTime() / 1_000_000L;
        SETTINGS_CHANGES.sweep(now);
        return SETTINGS_CHANGES.tryAcquire(player.getUniqueID(), now);
    }

    /** Whether {@code player} may open a carried phone's screen now, from the item or the Phone key. */
    public static boolean mayOpenScreen(EntityPlayer player) {
        long now = System.nanoTime() / 1_000_000L;
        SCREEN_OPENS.sweep(now);
        return SCREEN_OPENS.tryAcquire(player.getUniqueID(), now);
    }

    /**
     * Switches one of {@code player}'s own cameras during their call on this phone (DESIGN.md 10.1), on a phone with
     * video. Each switch re-plans every video call, so it shares the settings' rate limit.
     */
    public static void setCamera(EntityPlayer player, PhoneHandle handle, boolean rear, boolean on) {
        BeComServer becom = BeComServer.get();
        if (becom == null || !handle.usableBy(player)) return;
        PhoneKind kind = becom.calls.kindOf(handle.number());
        if (kind == null || !kind.video()) return;
        UUID id = player.getUniqueID();
        Call call = becom.calls.book()
            .callOf(id);
        Call.Leg own = call == null ? null : call.legOf(id);
        if (own == null || own.phone() != handle.number() || !mayChange(player)) return;
        if (rear) becom.video.setRear(id, on);
        else becom.video.setFront(id, on);
    }

    private static PhoneRecord managed(EntityPlayer player, PhoneHandle handle) {
        BeComServer becom = BeComServer.get();
        if (becom == null || !handle.usableBy(player) || !mayChange(player)) return null;
        PhoneRecord record = becom.phones.record(handle.number());
        if (record == null || !Ownership.mayManage(player, record.owner())) return null;
        return record;
    }

    public static void setDoNotDisturb(EntityPlayer player, PhoneHandle handle, boolean on) {
        PhoneRecord record = managed(player, handle);
        if (record == null) return;
        record.setDoNotDisturb(on);
        BeComServer.get().phones.registry()
            .changed();
    }

    public static void setHidden(EntityPlayer player, PhoneHandle handle, boolean on) {
        PhoneRecord record = managed(player, handle);
        if (record == null) return;
        record.setHidden(on);
        BeComServer.get().phones.registry()
            .changed();
    }

    /** Silent mode, for a carried phone (DESIGN.md 11.2): it buzzes for its holder instead of ringing out loud. */
    public static void setSilent(EntityPlayer player, PhoneHandle handle, boolean on) {
        if (!(handle instanceof PhoneHandle.CarriedPhone)) return;
        PhoneRecord record = managed(player, handle);
        if (record == null) return;
        record.setSilent(on);
        BeComServer.get().phones.registry()
            .changed();
    }

    /** Blocks the owner of a phone in this player's contacts (DESIGN.md 12.3, X9). */
    public static void blockContact(EntityPlayer player, PhoneHandle handle, int number) {
        PhoneRecord record = managed(player, handle);
        if (record == null) return;
        BeComServer becom = BeComServer.get();
        PhoneRegistry.Blockable target = becom.phones.registry()
            .contactTarget(player.getUniqueID(), record.number(), becom.calls.reachableFrom(record.number()), number);
        block(player, record, target);
    }

    /** Blocks whoever the recent call at {@code time} with {@code number} recorded on the other end. */
    public static void blockRecent(EntityPlayer player, PhoneHandle handle, long time, int number) {
        PhoneRecord record = managed(player, handle);
        if (record == null) return;
        block(
            player,
            record,
            BeComServer.get().phones.registry()
                .recentTarget(record, time, number));
    }

    private static void block(EntityPlayer player, PhoneRecord record, PhoneRegistry.Blockable target) {
        if (target == null) return;
        if (!record.block(target.player(), target.name())) {
            player.addChatMessage(new ChatComponentTranslation("becom.phone.block_full", PhoneRecord.MAX_BLOCKED));
            return;
        }
        BeComServer.get().phones.registry()
            .changed();
    }

    public static void unblock(EntityPlayer player, PhoneHandle handle, UUID blocked) {
        PhoneRecord record = managed(player, handle);
        if (record == null) return;
        if (record.unblock(blocked)) BeComServer.get().phones.registry()
            .changed();
    }
}
