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

    public static void clear() {
        LEVELS.clear();
    }
}
