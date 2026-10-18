package com.enn3developer.becom.gui;

import java.io.IOException;
import java.util.Arrays;
import java.util.function.Supplier;

import net.minecraft.network.PacketBuffer;

import com.cleanroommc.modularui.value.sync.SyncHandler;
import com.enn3developer.becom.config.BeComConfig;
import com.enn3developer.becom.packet.AnalyzerPacket;

import io.netty.buffer.Unpooled;

/**
 * Sends a carried phone's screen what a Signal Analyzer would read on its band there, for the Signal app: worked out
 * as often as an analyzer's, and sent when it changes. One way only.
 */
public final class SignalSync extends SyncHandler<SignalSync> {

    private static final int ID_READING = 0;
    private static final int TICKS_PER_SECOND = 20;

    private final Supplier<AnalyzerPacket.Reading> serverReading;
    private byte[] lastSent;
    private int ticks;
    private AnalyzerPacket.Reading clientReading = AnalyzerPacket.Reading.NONE;

    /** @param serverReading works the reading out on the server; never called on the client */
    public SignalSync(Supplier<AnalyzerPacket.Reading> serverReading) {
        this.serverReading = serverReading;
    }

    public AnalyzerPacket.Reading reading() {
        return clientReading;
    }

    @Override
    public void detectAndSendChanges(boolean init) {
        if (getSyncManager().isClient()) return;
        int every = Math.max(
            1,
            BeComConfig.get()
                .analyzerRefreshSeconds())
            * TICKS_PER_SECOND;
        if (!init && ticks++ % every != 0) return;
        PacketBuffer buf = new PacketBuffer(Unpooled.buffer());
        serverReading.get()
            .write(buf);
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        if (!init && Arrays.equals(bytes, lastSent)) return;
        lastSent = bytes;
        syncToClient(ID_READING, out -> out.writeBytes(bytes));
    }

    @Override
    public void readOnClient(int id, PacketBuffer buf) throws IOException {
        if (id == ID_READING) clientReading = AnalyzerPacket.Reading.read(buf);
    }

    @Override
    public void readOnServer(int id, PacketBuffer buf) {
        // Nothing comes from the client this way.
    }
}
