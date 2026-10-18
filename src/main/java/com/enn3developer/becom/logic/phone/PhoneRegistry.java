package com.enn3developer.becom.logic.phone;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import com.enn3developer.becom.logic.PhoneNumbers;

/**
 * Every phone in the world, by number. Server-thread only; the owner of the
 * registry saves it whenever {@link #version()} moves.
 */
public final class PhoneRegistry {

    private final Map<Integer, PhoneRecord> phones = new HashMap<>();
    private long version;

    /** A new phone with a fresh random number. */
    public PhoneRecord create(PhoneKind kind, UUID owner, String ownerName, Random random) {
        int number = PhoneNumbers.assign(random, phones::containsKey);
        PhoneRecord record = new PhoneRecord(number, kind, owner, ownerName);
        phones.put(number, record);
        version++;
        return record;
    }

    /** Puts back a phone read from saved data. */
    public void restore(PhoneRecord record) {
        phones.put(record.number(), record);
        version++;
    }

    public PhoneRecord get(int number) {
        return phones.get(number);
    }

    public PhoneRecord remove(int number) {
        PhoneRecord removed = phones.remove(number);
        if (removed != null) version++;
        return removed;
    }

    public Collection<PhoneRecord> all() {
        return Collections.unmodifiableCollection(phones.values());
    }

    public int size() {
        return phones.size();
    }

    public long version() {
        return version;
    }

    /** Marks a change made through a record, so it gets saved. */
    public void changed() {
        version++;
    }

    /** One line of a dialer's contacts list. */
    public record Contact(int number, String ownerName) {}

    /**
     * The contacts {@code caller} sees from {@code ownNumber}: every phone in {@code reachable} except its own, ones
     * hidden from contacts, and ones whose block list has the caller. Sorted by owner name, then
     * number.
     */
    public List<Contact> contacts(UUID caller, int ownNumber, Collection<Integer> reachable) {
        List<Contact> contacts = new ArrayList<>();
        for (int number : reachable) {
            if (number == ownNumber) continue;
            PhoneRecord record = phones.get(number);
            if (record == null || record.hidden()
                || record.blocks(caller)
                || record.kind() == PhoneKind.COMPUTER
                || record.kind() == PhoneKind.MIRROR) continue;
            contacts.add(new Contact(number, record.ownerName()));
        }
        contacts.sort(
            Comparator.comparing(Contact::ownerName, String.CASE_INSENSITIVE_ORDER)
                .thenComparingInt(Contact::number));
        return contacts;
    }

    /** A player the phone screen may block, and the name the block list shows. */
    public record Blockable(UUID player, String name) {}

    /**
     * Who blocking a row of {@code own}'s recent calls blocks: the player the row recorded on the other end (whoever
     * called or answered, or for an unanswered outgoing call the other phone's owner at the time). The row is named by
     * its time and number, which survive new calls landing on top. Null when there's no such row, it recorded nobody,
     * or it would block the phone's own owner. The client never names a player, so a modified client can only block
     * someone this phone has had a call with.
     */
    public Blockable recentTarget(PhoneRecord own, long time, int number) {
        for (PhoneRecord.HistoryEntry entry : own.history()) {
            if (entry.time() != time || entry.otherNumber() != number) continue;
            if (entry.otherPlayer() == null || entry.otherPlayer()
                .equals(own.owner())) return null;
            return new Blockable(entry.otherPlayer(), entry.otherName());
        }
        return null;
    }

    /**
     * Who blocking a contact blocks: the owner of phone {@code number}, but only if it is in the contacts
     * {@code caller} sees right now. Anything else could tell a modified client who owns a hidden number.
     */
    public Blockable contactTarget(UUID caller, int ownNumber, Collection<Integer> reachable, int number) {
        PhoneRecord own = phones.get(ownNumber);
        for (Contact contact : contacts(caller, ownNumber, reachable)) {
            if (contact.number() != number) continue;
            PhoneRecord other = phones.get(number);
            if (other == null || other.owner() == null
                || own != null && other.owner()
                    .equals(own.owner()))
                return null;
            return new Blockable(other.owner(), other.ownerName());
        }
        return null;
    }

    /** Why a call can't ring a phone, before any network check. */
    public enum Refusal {
        NONE,
        NO_SUCH_NUMBER,
        DO_NOT_DISTURB,
        BLOCKED
    }

    public Refusal refusal(UUID caller, int callee) {
        PhoneRecord record = phones.get(callee);
        if (record == null) return Refusal.NO_SUCH_NUMBER;
        if (record.doNotDisturb()) return Refusal.DO_NOT_DISTURB;
        if (record.blocks(caller)) return Refusal.BLOCKED;
        return Refusal.NONE;
    }
}
