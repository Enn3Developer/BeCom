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
 * One phone in the world's registry (DESIGN.md 8.2, 12.3): its number, owner, where a block phone stands, its
 * settings and its recent calls. Server-thread only.
 */
public final class PhoneRecord {

    /** How many recent calls a phone remembers. */
    public static final int HISTORY_SIZE = 32;
    /** How many players one phone can block; enough for any grudge, small enough for one screen packet. */
    public static final int MAX_BLOCKED = 128;

    private final int number;
    private final PhoneKind kind;
    private UUID owner;
    private String ownerName;
    private int dimension;
    private long pos;
    private boolean doNotDisturb;
    private boolean hidden;
    private final Map<UUID, String> blocked = new LinkedHashMap<>();
    private final Deque<HistoryEntry> history = new ArrayDeque<>();

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

    /** Newest first. */
    public List<HistoryEntry> history() {
        return new ArrayList<>(history);
    }

    public void addHistory(HistoryEntry entry) {
        history.addFirst(entry);
        while (history.size() > HISTORY_SIZE) history.removeLast();
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
