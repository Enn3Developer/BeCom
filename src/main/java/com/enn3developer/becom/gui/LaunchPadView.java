package com.enn3developer.becom.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.PacketBuffer;

/**
 * What the Satellite Launch Pad's screen shows (DESIGN.md 9.1, 11.4): the rockets waiting to launch, and each
 * dimension's satellites with the time until the next comes down. Built on the server, translated on the client.
 */
public record LaunchPadView(int rocketsWaiting, List<Orbit> orbits) {

    /** One dimension's satellites: its name, how many, and ticks until the next comes down. */
    public record Orbit(String dimension, int satellites, long untilNextDown) {}

    public static final LaunchPadView EMPTY = new LaunchPadView(0, List.of());

    // More dimensions than a screen shows; it also bounds what a client reads.
    public static final int MAX_ORBITS = 64;

    public void write(PacketBuffer buf) {
        buf.writeInt(rocketsWaiting);
        int count = Math.min(orbits.size(), MAX_ORBITS);
        buf.writeShort(count);
        for (int i = 0; i < count; i++) {
            Orbit orbit = orbits.get(i);
            AntennaView.writeString(buf, orbit.dimension());
            buf.writeInt(orbit.satellites());
            buf.writeLong(orbit.untilNextDown());
        }
    }

    public static LaunchPadView read(PacketBuffer buf) {
        int rockets = buf.readInt();
        int count = Math.min(buf.readShort(), MAX_ORBITS);
        List<Orbit> orbits = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            orbits.add(new Orbit(AntennaView.readString(buf), buf.readInt(), buf.readLong()));
        }
        return new LaunchPadView(rockets, orbits);
    }
}
