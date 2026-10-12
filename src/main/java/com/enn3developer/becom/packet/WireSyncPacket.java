package com.enn3developer.becom.packet;

import java.util.ArrayList;
import java.util.List;

import com.enn3developer.becom.BeCom;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Every pylon wire of one dimension, so clients can draw them (DESIGN.md 5.1): sent to a player entering the dimension
 * and to everyone in it when a link or its clearance changes.
 */
public final class WireSyncPacket implements IMessage {

    /** One wire between two heads; a blocked one renders broken (D20). */
    public record Wire(long a, long b, boolean blocked) {}

    // A wire is 17 bytes; this keeps one packet well under the 2 MB a server packet may carry.
    private static final int MAX_WIRES = 100_000;

    private int dimension;
    private List<Wire> wires;

    public WireSyncPacket() {}

    public WireSyncPacket(int dimension, List<Wire> wires) {
        this.dimension = dimension;
        this.wires = wires.size() > MAX_WIRES ? wires.subList(0, MAX_WIRES) : wires;
    }

    public int dimension() {
        return dimension;
    }

    public List<Wire> wires() {
        return wires;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        dimension = buf.readInt();
        int count = Math.min(buf.readInt(), MAX_WIRES);
        wires = new ArrayList<>(count);
        for (int i = 0; i < count; i++) wires.add(new Wire(buf.readLong(), buf.readLong(), buf.readBoolean()));
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(dimension);
        buf.writeInt(wires.size());
        for (Wire wire : wires) {
            buf.writeLong(wire.a());
            buf.writeLong(wire.b());
            buf.writeBoolean(wire.blocked());
        }
    }

    public static final class Handler implements IMessageHandler<WireSyncPacket, IMessage> {

        @Override
        public IMessage onMessage(WireSyncPacket message, MessageContext ctx) {
            BeCom.proxy.onWires(message);
            return null;
        }
    }
}
