package com.enn3developer.becom.client;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.enn3developer.becom.logic.SignalLevel;

/**
 * The level this player hears each source at through each BeCom group (DESIGN.md 13.5), filled by the server's level
 * packets and read by the playback chain on GTNH Voice's decode threads, hence concurrent.
 */
public final class ClientLevels {

    private static final Map<Key, SignalLevel> LEVELS = new ConcurrentHashMap<>();

    private ClientLevels() {}

    private record Key(UUID source, String group) {}

    public static void set(UUID source, String group, SignalLevel level) {
        if (level == SignalLevel.NONE) LEVELS.remove(new Key(source, group));
        else LEVELS.put(new Key(source, group), level);
    }

    /** The level for a source and group, or {@link SignalLevel#NONE} when BeCom told this client nothing. */
    public static SignalLevel get(UUID source, String group) {
        SignalLevel level = LEVELS.get(new Key(source, group));
        return level == null ? SignalLevel.NONE : level;
    }

    /**
     * The level to filter a radio frame at. A frame can arrive before its level: the server tells levels once a
     * second, and someone can walk up to a receiving walkie in between. Radios never sound better than Medium (D10),
     * so that's what an unknown one plays at, never clean.
     */
    public static SignalLevel radio(UUID source, String group) {
        SignalLevel level = get(source, group);
        return level == SignalLevel.NONE ? SignalLevel.MEDIUM : level;
    }

    /** The weakest level this player hears anyone at through {@code group}, or NONE when it hears no one. */
    public static SignalLevel weakest(String group) {
        SignalLevel weakest = null;
        for (Map.Entry<Key, SignalLevel> entry : LEVELS.entrySet()) {
            if (!entry.getKey().group.equals(group)) continue;
            weakest = weakest == null ? entry.getValue() : SignalLevel.min(weakest, entry.getValue());
        }
        return weakest == null ? SignalLevel.NONE : weakest;
    }

    public static void clear() {
        LEVELS.clear();
    }
}
