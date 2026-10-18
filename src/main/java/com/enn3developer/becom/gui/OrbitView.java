package com.enn3developer.becom.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.PacketBuffer;

import com.enn3developer.becom.logic.SignalLevel;
import com.enn3developer.becom.logic.space.Forecast;

/**
 * What the Launch Pad's Orbit page shows, from the Orbit board: each sky with its satellites, its level, its calls and
 * who's using it, then the pad's own sky over the next days, if nothing more goes up. Built on the server.
 *
 * @param skies          each sky, the pad's first
 * @param here           the pad's dimension's name, whose sky the forecast is for
 * @param forecast       how the pad's sky thins out
 * @param satelliteCalls the calls each satellite carries
 * @param lifetimeDays   about how long a satellite stays up
 */
public record OrbitView(List<Sky> skies, String here, Forecast forecast, int satelliteCalls, int lifetimeDays) {

    /**
     * One sky.
     *
     * @param up        satellites over it
     * @param needs     the fewest that give a hop: more than {@code up} when it has none
     * @param calls     calls the sky carries now
     * @param slots     calls it can carry at once
     * @param dishes    working Satellite Dishes under it
     * @param uplinks   the planets whose receivers uplink to it, each with how many
     * @param underSky  carried phones reaching it on their own
     * @param uplinksTo the sky this one's receivers uplink to instead, or empty
     */
    public record Sky(String name, int up, SignalLevel level, int needs, int calls, int slots, int dishes,
        List<Uplink> uplinks, int underSky, String uplinksTo) {}

    /** A planet whose receivers uplink to a sky, and how many. */
    public record Uplink(String name, int receivers) {}

    public static final OrbitView EMPTY = new OrbitView(
        List.of(),
        "",
        new Forecast(List.of(), 0, SignalLevel.NONE, -1, 0, 0, 0),
        0,
        0);

    // More than a page shows; it also bounds what a client reads.
    public static final int MAX_SKIES = 32;
    private static final int MAX_FALLS = 512;
    private static final int MAX_UPLINKS = 16;

    public void write(PacketBuffer buf) {
        int count = Math.min(skies.size(), MAX_SKIES);
        buf.writeByte(count);
        for (int i = 0; i < count; i++) {
            Sky sky = skies.get(i);
            AntennaView.writeString(buf, sky.name());
            buf.writeShort(sky.up());
            buf.writeByte(
                sky.level()
                    .ordinal());
            buf.writeShort(sky.needs());
            buf.writeShort(sky.calls());
            buf.writeShort(sky.slots());
            buf.writeShort(sky.dishes());
            int uplinks = Math.min(
                sky.uplinks()
                    .size(),
                MAX_UPLINKS);
            buf.writeByte(uplinks);
            for (int u = 0; u < uplinks; u++) {
                Uplink uplink = sky.uplinks()
                    .get(u);
                AntennaView.writeString(buf, uplink.name());
                buf.writeShort(uplink.receivers());
            }
            buf.writeShort(sky.underSky());
            AntennaView.writeString(buf, sky.uplinksTo());
        }
        AntennaView.writeString(buf, here);
        int falls = Math.min(
            forecast.fallDays()
                .size(),
            MAX_FALLS);
        buf.writeShort(falls);
        for (int i = 0; i < falls; i++) buf.writeShort(
            forecast.fallDays()
                .get(i));
        buf.writeShort(forecast.threshold());
        buf.writeByte(
            forecast.below()
                .ordinal());
        buf.writeShort(forecast.dropDay());
        buf.writeShort(forecast.together());
        buf.writeShort(forecast.callsNow());
        buf.writeShort(forecast.callsThen());
        buf.writeShort(satelliteCalls);
        buf.writeShort(lifetimeDays);
    }

    private static SignalLevel level(int ordinal) {
        SignalLevel[] levels = SignalLevel.values();
        return levels[Math.max(0, Math.min(levels.length - 1, ordinal))];
    }

    public static OrbitView read(PacketBuffer buf) {
        int count = Math.min(buf.readUnsignedByte(), MAX_SKIES);
        List<Sky> skies = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String name = AntennaView.readString(buf);
            int up = buf.readShort();
            SignalLevel level = level(buf.readByte());
            int needs = buf.readShort();
            int calls = buf.readShort();
            int slots = buf.readShort();
            int dishes = buf.readShort();
            int uplinkCount = Math.min(buf.readUnsignedByte(), MAX_UPLINKS);
            List<Uplink> uplinks = new ArrayList<>(uplinkCount);
            for (int u = 0; u < uplinkCount; u++) uplinks.add(new Uplink(AntennaView.readString(buf), buf.readShort()));
            int underSky = buf.readShort();
            skies.add(
                new Sky(name, up, level, needs, calls, slots, dishes, uplinks, underSky, AntennaView.readString(buf)));
        }
        String here = AntennaView.readString(buf);
        int falls = Math.min(buf.readShort(), MAX_FALLS);
        List<Integer> fallDays = new ArrayList<>(Math.max(0, falls));
        for (int i = 0; i < falls; i++) fallDays.add((int) buf.readShort());
        Forecast forecast = new Forecast(
            fallDays,
            buf.readShort(),
            level(buf.readByte()),
            buf.readShort(),
            buf.readShort(),
            buf.readShort(),
            buf.readShort());
        return new OrbitView(skies, here, forecast, buf.readShort(), buf.readShort());
    }
}
