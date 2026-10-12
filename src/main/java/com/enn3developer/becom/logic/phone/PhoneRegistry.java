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
 * Every phone in the world, by number (D12: numbers are unique per world). Server-thread only; the owner of the
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

    /** One line of a dialer's contacts list (D25). */
    public record Contact(int number, String ownerName) {}

    /**
     * The contacts {@code caller} sees from {@code ownNumber}: every phone in {@code reachable} except its own, ones
     * hidden from contacts, and ones whose block list has the caller (DESIGN.md 12.3). Sorted by owner name, then
     * number.
     */
    public List<Contact> contacts(UUID caller, int ownNumber, Collection<Integer> reachable) {
        List<Contact> contacts = new ArrayList<>();
        for (int number : reachable) {
            if (number == ownNumber) continue;
            PhoneRecord record = phones.get(number);
            if (record == null || record.hidden() || record.blocks(caller)) continue;
            contacts.add(new Contact(number, record.ownerName()));
        }
        contacts.sort(
            Comparator.comparing(Contact::ownerName, String.CASE_INSENSITIVE_ORDER)
                .thenComparingInt(Contact::number));
        return contacts;
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
