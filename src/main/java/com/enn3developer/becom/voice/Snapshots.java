package com.enn3developer.becom.voice;

import java.util.Map;
import java.util.UUID;

import com.enn3developer.becom.logic.Positions;
import com.enn3developer.gtnhvoice.api.server.PlayerSnapshot;

/** Players' positions as BeCom takes them from a position snapshot: one that isn't finite is no position at all. */
final class Snapshots {

    private Snapshots() {}

    /** {@code player}'s position, or null when the snapshot has none for them or it isn't finite. */
    static PlayerSnapshot of(Map<UUID, PlayerSnapshot> positions, UUID player) {
        PlayerSnapshot p = positions.get(player);
        return placed(p) ? p : null;
    }

    /** Whether {@code p} is a position at all: there, and finite. */
    static boolean placed(PlayerSnapshot p) {
        return p != null && Positions.finite(p.getX(), p.getY(), p.getZ());
    }
}
