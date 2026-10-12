package com.enn3developer.becom.client;

import java.util.Collections;
import java.util.List;

import com.enn3developer.becom.packet.WireSyncPacket;

/** The pylon wires of the dimension this client is in, as the server last sent them. Client thread only. */
public final class ClientWires {

    private static int dimension = Integer.MIN_VALUE;
    private static List<WireSyncPacket.Wire> wires = Collections.emptyList();

    private ClientWires() {}

    public static void set(int dim, List<WireSyncPacket.Wire> list) {
        dimension = dim;
        wires = list;
    }

    public static List<WireSyncPacket.Wire> wires(int currentDimension) {
        return currentDimension == dimension ? wires : Collections.emptyList();
    }

    public static void clear() {
        dimension = Integer.MIN_VALUE;
        wires = Collections.emptyList();
    }
}
