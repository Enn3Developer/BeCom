package com.enn3developer.becom.logic.video;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.IntFunction;

import com.enn3developer.becom.logic.SignalLevel;
import com.enn3developer.becom.logic.call.Call;
import com.enn3developer.becom.logic.phone.PhoneKind;

/**
 * Who sees whom in a video call: every player on a video phone sees everyone on the other
 * video phones of their call, each at the level they hear that phone at. Video needs Medium or better: at Low the
 * picture freezes, and the client says so. The rear camera streams only between the two people of a one-to-one call,
 * from a phone they carry, at High or better. Built from a call's state; pure, so it's tested without a world.
 */
public final class VideoPlan {

    /** The cameras a player has switched on: the front starts on, the rear off. */
    public record Cameras(boolean front, boolean rear) {

        public static final Cameras START = new Cameras(true, false);
    }

    /**
     * What one viewer sees of one person in their call: the phone they're on, the level the viewer gets them at, and
     * which of their cameras reach the viewer. {@code front} is the switch; at Low the picture is frozen whatever it
     * says. {@code rear} is set only when rear frames really go to this viewer.
     */
    public record Tile(UUID source, int phone, SignalLevel level, boolean front, boolean rear) {

        /** Whether the front camera's picture moves: switched on and at Medium or better. */
        public boolean live() {
            return front && level.atLeast(SignalLevel.MEDIUM);
        }
    }

    public static final VideoPlan EMPTY = new VideoPlan(Collections.emptyMap(), Collections.emptyMap());

    private final Map<UUID, List<Tile>> tiles;
    private final Map<UUID, SignalLevel> rearSends;

    private VideoPlan(Map<UUID, List<Tile>> tiles, Map<UUID, SignalLevel> rearSends) {
        this.tiles = tiles;
        this.rearSends = rearSends;
    }

    /**
     * The plan for one call.
     *
     * @param kinds   each phone's kind, null when it has none
     * @param cameras each player's switches
     */
    public static VideoPlan of(Call call, IntFunction<PhoneKind> kinds, Function<UUID, Cameras> cameras) {
        if (call.state() != Call.State.CONNECTED) return EMPTY;
        List<Call.Leg> video = new ArrayList<>();
        for (Call.Leg leg : call.activeLegs()) {
            PhoneKind kind = kinds.apply(leg.phone());
            if (kind != null && kind.video()) video.add(leg);
        }
        if (video.size() < 2) return EMPTY;
        // One-to-one: two phones and two people in all, so a rear stream has one viewer and bandwidth stays bounded.
        boolean oneToOne = call.activeLegs()
            .size() == 2
            && call.participants()
                .size() == 2;
        Map<UUID, List<Tile>> tiles = new HashMap<>();
        Map<UUID, SignalLevel> rearSends = new HashMap<>();
        for (Call.Leg viewing : video) {
            for (Call.Leg seen : video) {
                if (seen == viewing) continue;
                SignalLevel level = call.level(seen, viewing);
                if (level == SignalLevel.NONE) continue;
                boolean rearPhone = oneToOne && kinds.apply(seen.phone())
                    .carried();
                for (UUID source : seen.players()) {
                    Cameras switches = cameras.apply(source);
                    boolean rear = rearPhone && switches.rear() && level.atLeast(SignalLevel.HIGH);
                    if (rear) rearSends.put(source, level);
                    Tile tile = new Tile(source, seen.phone(), level, switches.front(), rear);
                    for (UUID viewer : viewing.players()) {
                        tiles.computeIfAbsent(viewer, v -> new ArrayList<>())
                            .add(tile);
                    }
                }
            }
        }
        return new VideoPlan(tiles, rearSends);
    }

    /** Joins the plans of several calls; a player is in one call at most. */
    public static VideoPlan merge(List<VideoPlan> plans) {
        Map<UUID, List<Tile>> tiles = new HashMap<>();
        Map<UUID, SignalLevel> rearSends = new HashMap<>();
        for (VideoPlan plan : plans) {
            tiles.putAll(plan.tiles);
            rearSends.putAll(plan.rearSends);
        }
        return new VideoPlan(tiles, rearSends);
    }

    /** What {@code viewer} sees, in the call's order of phones; empty when they're in no video call. */
    public List<Tile> tilesOf(UUID viewer) {
        List<Tile> found = tiles.get(viewer);
        return found == null ? Collections.emptyList() : Collections.unmodifiableList(found);
    }

    /** Whether {@code player} sees anyone. */
    public boolean sees(UUID player) {
        return tiles.containsKey(player);
    }

    /** Everyone who sees anyone. */
    public Iterable<UUID> viewers() {
        return Collections.unmodifiableSet(tiles.keySet());
    }

    /**
     * Whether {@code viewer} gets live video from someone in their call: both on video phones, that person's front
     * camera on, at Medium or better. What completes the Video call quest task for them.
     */
    public boolean seesLive(UUID viewer) {
        for (Tile tile : tilesOf(viewer)) if (tile.live()) return true;
        return false;
    }

    /** The level {@code sender}'s rear camera goes out at, or null when it goes to nobody. */
    public SignalLevel rearSendOf(UUID sender) {
        return rearSends.get(sender);
    }

    /** The viewers {@code source} reaches with live front-camera video, and at which level each. */
    public Map<UUID, Tile> liveViewersOf(UUID source) {
        Map<UUID, Tile> viewers = new HashMap<>();
        for (Map.Entry<UUID, List<Tile>> entry : tiles.entrySet()) {
            for (Tile tile : entry.getValue()) {
                if (tile.source()
                    .equals(source) && tile.live()) viewers.put(entry.getKey(), tile);
            }
        }
        return viewers;
    }
}
