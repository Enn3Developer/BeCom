package com.enn3developer.becom.logic.phone;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One phone in the world's registry: its number, owner, where a block phone stands, its
 * settings and its recent calls. Server-thread only.
 */
public final class PhoneRecord {

    /** How many recent calls a phone remembers. */
    public static final int HISTORY_SIZE = 32;
    /** How many players one phone can block; enough for any grudge, small enough for one screen packet. */
    public static final int MAX_BLOCKED = 128;
    /** Speed-dial keys on a desk phone: M1 to M4 on a Telephone, M1 to M3 on a Videophone. */
    public static final int SPEED_DIAL_KEYS = 4;

    private final int number;
    private final PhoneKind kind;
    private UUID owner;
    private String ownerName;
    private int dimension;
    private long pos;
    private boolean doNotDisturb;
    private boolean hidden;
    private boolean silent;
    private final Map<UUID, String> blocked = new LinkedHashMap<>();
    private final Deque<HistoryEntry> history = new ArrayDeque<>();
    // The number each speed-dial key stores, 0 for none.
    private final int[] speedDial = new int[SPEED_DIAL_KEYS];
    // A Videophone's camera: on, its calls start as video calls.
    private boolean camera = true;
    // When its owner last saw its missed calls, by the history's wall clock; a missed call after it is new.
    private long missedSeen;

    public PhoneRecord(int number, PhoneKind kind, UUID owner, String ownerName) {
        this.number = number;
        this.kind = kind;
        this.owner = owner;
        this.ownerName = ownerName == null ? "" : ownerName;
    }

    public int number() {
        return number;
    }

    public PhoneKind kind() {
        return kind;
    }

    public UUID owner() {
        return owner;
    }

    public String ownerName() {
        return ownerName;
    }

    public void setOwner(UUID owner, String ownerName) {
        this.owner = owner;
        this.ownerName = ownerName == null ? "" : ownerName;
    }

    public int dimension() {
        return dimension;
    }

    public long pos() {
        return pos;
    }

    public void setLocation(int dimension, long pos) {
        this.dimension = dimension;
        this.pos = pos;
    }

    public boolean doNotDisturb() {
        return doNotDisturb;
    }

    public void setDoNotDisturb(boolean doNotDisturb) {
        this.doNotDisturb = doNotDisturb;
    }

    public boolean hidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    /** Silent mode: a carried phone rings only for its holder, as a short buzz. */
    public boolean silent() {
        return silent;
    }

    public void setSilent(boolean silent) {
        this.silent = silent;
    }

    /** Blocked players, by UUID, with the name they had when blocked. */
    public Map<UUID, String> blocked() {
        return Collections.unmodifiableMap(blocked);
    }

    public boolean blocks(UUID player) {
        return player != null && blocked.containsKey(player);
    }

    /** Blocks {@code player}; false when the list is full. */
    public boolean block(UUID player, String name) {
        if (!blocked.containsKey(player) && blocked.size() >= MAX_BLOCKED) return false;
        blocked.put(player, name == null ? "" : name);
        return true;
    }

    /** Puts back a block read from saved data, past the cap if that's how it was saved. */
    public void restoreBlock(UUID player, String name) {
        blocked.put(player, name == null ? "" : name);
    }

    public boolean unblock(UUID player) {
        return blocked.remove(player) != null;
    }

    /** The number speed-dial key {@code key} (0 for M1) stores, or 0. */
    public int speedDial(int key) {
        return key >= 0 && key < SPEED_DIAL_KEYS ? speedDial[key] : 0;
    }

    /** Stores {@code number} on speed-dial key {@code key}, 0 to clear it. */
    public void setSpeedDial(int key, int number) {
        if (key >= 0 && key < SPEED_DIAL_KEYS) speedDial[key] = Math.max(0, number);
    }

    /** A Videophone's Camera key: on, the calls it makes and answers are video calls. */
    public boolean camera() {
        return camera;
    }

    public void setCamera(boolean camera) {
        this.camera = camera;
    }

    /** When its owner last saw its missed calls, by the history's wall clock. */
    public long missedSeen() {
        return missedSeen;
    }

    public void setMissedSeen(long time) {
        missedSeen = time;
    }

    /** How many missed calls came after its owner last saw them. */
    public int newMissed() {
        int count = 0;
        for (HistoryEntry entry : history)
            if (entry.direction() == Direction.MISSED && entry.time() > missedSeen) count++;
        return count;
    }

    /** Newest first. */
    public List<HistoryEntry> history() {
        return new ArrayList<>(history);
    }

    /** Puts back an entry read from saved data, after those already read (saved newest first), without trimming. */
    public void restoreHistory(HistoryEntry entry) {
        history.addLast(entry);
    }

    /**
     * Adds a call on top, dropping the oldest past the cap. A history loaded longer than the cap (a newer BeCom kept
     * more) rolls at the length it was loaded with rather than being cut back.
     */
    public void addHistory(HistoryEntry entry) {
        int keep = Math.max(HISTORY_SIZE, history.size());
        history.addFirst(entry);
        while (history.size() > keep) history.removeLast();
    }

    public enum Direction {
        OUTGOING,
        INCOMING,
        MISSED
    }

    /**
     * One call this phone made, took or missed: the other phone, and who was on it, by name and UUID: the player who
     * called or answered, or for an outgoing call nobody answered, that phone's owner at the time. The player is null
     * when there was nobody to record, or for entries saved before players were recorded.
     */
    public record HistoryEntry(long time, int otherNumber, String otherName, Direction direction, UUID otherPlayer) {}
}
