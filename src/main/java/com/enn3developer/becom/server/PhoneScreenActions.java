package com.enn3developer.becom.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentTranslation;

import com.enn3developer.becom.block.TileTelephone;
import com.enn3developer.becom.gui.PhoneView;
import com.enn3developer.becom.logic.PhoneNumbers;
import com.enn3developer.becom.logic.SignalLevel;
import com.enn3developer.becom.logic.call.Call;
import com.enn3developer.becom.logic.phone.PhoneRecord;
import com.enn3developer.becom.logic.phone.PhoneRegistry;

/**
 * What the phone screen may do, checked on the server (DESIGN.md 14: client input). Every action re-checks that the
 * player is at the phone and allowed to do it, whatever their client sent.
 */
public final class PhoneScreenActions {

    // The same 8 blocks ModularUI allows a block screen to stay open from.
    private static final double REACH_SQ = 64;

    private PhoneScreenActions() {}

    private static boolean near(EntityPlayer player, TileTelephone tile) {
        return !tile.isInvalid() && player.worldObj == tile.getWorldObj()
            && player.getDistanceSq(tile.xCoord + 0.5, tile.yCoord + 0.5, tile.zCoord + 0.5) <= REACH_SQ;
    }

    public static PhoneView view(EntityPlayer player, TileTelephone tile) {
        BeComServer becom = BeComServer.get();
        if (becom == null || tile.number() < 0) return PhoneView.EMPTY;
        int number = tile.number();
        PhoneRecord record = becom.phones.record(number);
        if (record == null) return PhoneView.EMPTY;
        boolean manage = Ownership.mayManage(player, record.owner());
        UUID holder = becom.calls.holderOf(number);
        Call call = becom.calls.book()
            .callOfPhone(number);
        PhoneView.State state;
        int otherNumber = -1;
        SignalLevel hearing = SignalLevel.NONE;
        if (call != null) {
            for (Call.Leg leg : call.legs()) if (leg.phone() != number) otherNumber = leg.phone();
            if (call.state() == Call.State.RINGING) {
                state = call.caller()
                    .phone() == number ? PhoneView.State.RINGING_OUT : PhoneView.State.RINGING_IN;
            } else {
                state = PhoneView.State.CONNECTED;
                Call.Leg other = call.legOf(otherNumber);
                if (other != null && other.player() != null) hearing = call.level(other.player(), player.getUniqueID());
            }
        } else if (holder != null) {
            state = holder.equals(player.getUniqueID()) ? PhoneView.State.OFF_HOOK : PhoneView.State.IN_USE;
        } else {
            state = PhoneView.State.IDLE;
        }
        List<PhoneView.Contact> contacts = new ArrayList<>();
        for (PhoneRegistry.Contact contact : becom.phones.registry()
            .contacts(player.getUniqueID(), number, becom.calls.reachableFrom(number))) {
            contacts.add(new PhoneView.Contact(contact.number(), contact.ownerName()));
        }
        // Calls are private to the people in them (DESIGN.md 12.2): a phone's history is for its owner and their team.
        List<PhoneView.Recent> recent = new ArrayList<>();
        if (manage) {
            for (PhoneRecord.HistoryEntry entry : record.history()) {
                recent.add(new PhoneView.Recent(entry.otherNumber(), entry.otherName(), entry.direction()));
            }
        }
        List<PhoneView.Blocked> blocked = new ArrayList<>();
        if (manage) {
            for (Map.Entry<UUID, String> entry : record.blocked()
                .entrySet()) blocked.add(new PhoneView.Blocked(entry.getKey(), entry.getValue()));
        }
        return new PhoneView(
            number,
            record.ownerName(),
            manage,
            state,
            otherNumber < 0 ? "" : becom.phones.name(otherNumber),
            otherNumber,
            hearing,
            record.doNotDisturb(),
            record.hidden(),
            contacts,
            recent,
            blocked);
    }

    public static void dial(EntityPlayer player, TileTelephone tile, int target) {
        BeComServer becom = BeComServer.get();
        if (becom == null || !(player instanceof EntityPlayerMP mp) || !near(player, tile)) return;
        if (!PhoneNumbers.isValid(target)) return;
        Integer held = becom.calls.handsetOf(player.getUniqueID());
        if (held == null) {
            if (becom.calls.takeHandset(mp, tile) != CallService.Pickup.TAKEN) return;
        } else if (held != tile.number()) {
            return;
        }
        becom.calls.dial(mp, tile.number(), target);
    }

    /** Picks up: answers a ringing phone, or takes the handset to dial. */
    public static void pickUp(EntityPlayer player, TileTelephone tile) {
        BeComServer becom = BeComServer.get();
        if (becom == null || !(player instanceof EntityPlayerMP mp) || !near(player, tile)) return;
        becom.calls.takeHandset(mp, tile);
    }

    public static void hangUp(EntityPlayer player, TileTelephone tile) {
        BeComServer becom = BeComServer.get();
        if (becom == null || !near(player, tile)) return;
        Integer held = becom.calls.handsetOf(player.getUniqueID());
        if (held != null && held == tile.number()) becom.calls.hangUpHandset(player.getUniqueID());
    }

    private static PhoneRecord managed(EntityPlayer player, TileTelephone tile) {
        BeComServer becom = BeComServer.get();
        if (becom == null || !near(player, tile)) return null;
        PhoneRecord record = becom.phones.record(tile.number());
        if (record == null || !Ownership.mayManage(player, record.owner())) return null;
        return record;
    }

    public static void setDoNotDisturb(EntityPlayer player, TileTelephone tile, boolean on) {
        PhoneRecord record = managed(player, tile);
        if (record == null) return;
        record.setDoNotDisturb(on);
        BeComServer.get().phones.registry()
            .changed();
    }

    public static void setHidden(EntityPlayer player, TileTelephone tile, boolean on) {
        PhoneRecord record = managed(player, tile);
        if (record == null) return;
        record.setHidden(on);
        BeComServer.get().phones.registry()
            .changed();
    }

    /** Blocks the owner of a phone in this player's contacts (DESIGN.md 12.3, X9). */
    public static void blockContact(EntityPlayer player, TileTelephone tile, int number) {
        PhoneRecord record = managed(player, tile);
        if (record == null) return;
        BeComServer becom = BeComServer.get();
        PhoneRegistry.Blockable target = becom.phones.registry()
            .contactTarget(player.getUniqueID(), record.number(), becom.calls.reachableFrom(record.number()), number);
        block(player, record, target);
    }

    /** Blocks the player on the other end of row {@code index} of this phone's recent calls, if it still shows. */
    public static void blockRecent(EntityPlayer player, TileTelephone tile, int index, int number) {
        PhoneRecord record = managed(player, tile);
        if (record == null) return;
        block(
            player,
            record,
            BeComServer.get().phones.registry()
                .recentTarget(record, index, number));
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

    public static void unblock(EntityPlayer player, TileTelephone tile, UUID blocked) {
        PhoneRecord record = managed(player, tile);
        if (record == null) return;
        if (record.unblock(blocked)) BeComServer.get().phones.registry()
            .changed();
    }
}
