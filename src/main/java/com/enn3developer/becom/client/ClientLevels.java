package com.enn3developer.becom.client;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.enn3developer.becom.logic.SignalLevel;

/**
 * The level this player hears each source at through each BeCom group, filled by the server's level
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
     * second, and someone can walk up to a receiving walkie in between. Radios never sound better than Medium,
     * so that's what an unknown one plays at, never clean.
     */
    public static SignalLevel radio(UUID source, String group) {
        SignalLevel level = get(source, group);
        return level == SignalLevel.NONE ? SignalLevel.MEDIUM : level;
    }

    /**
     * The level to filter a call frame at. A call's first frames can arrive before its levels, which come over the game
     * connection; they play at Low, the lowest a connected call sounds, never clean.
     */
    public static SignalLevel call(UUID source, String group) {
        SignalLevel level = get(source, group);
        return level == SignalLevel.NONE ? SignalLevel.LOW : level;
    }

    public static void clear() {
        LEVELS.clear();
    }
}
