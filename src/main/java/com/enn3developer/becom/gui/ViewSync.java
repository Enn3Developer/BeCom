package com.enn3developer.becom.gui;

import java.io.IOException;
import java.util.Arrays;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import net.minecraft.network.PacketBuffer;

import com.cleanroommc.modularui.value.sync.SyncHandler;

import io.netty.buffer.Unpooled;

/**
 * Sends a screen's view from server to client whenever its bytes change. One way only: the client acts through synced
 * actions the server checks (DESIGN.md 14: client input).
 */
public final class ViewSync<T> extends SyncHandler<ViewSync<T>> {

    private static final int ID_VIEW = 0;

    private final Supplier<T> serverView;
    private final BiConsumer<T, PacketBuffer> writer;
    private final Function<PacketBuffer, T> reader;
    private byte[] lastSent;
    private T clientView;

    /** @param serverView builds the view on the server; never called on the client */
    public ViewSync(Supplier<T> serverView, BiConsumer<T, PacketBuffer> writer, Function<PacketBuffer, T> reader,
        T empty) {
        this.serverView = serverView;
        this.writer = writer;
        this.reader = reader;
        this.clientView = empty;
    }

    public T view() {
        return clientView;
    }

    @Override
    public void detectAndSendChanges(boolean init) {
        if (getSyncManager().isClient()) return;
        PacketBuffer buf = new PacketBuffer(Unpooled.buffer());
        writer.accept(serverView.get(), buf);
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        if (!init && Arrays.equals(bytes, lastSent)) return;
        lastSent = bytes;
        syncToClient(ID_VIEW, out -> out.writeBytes(bytes));
    }

    @Override
    public void readOnClient(int id, PacketBuffer buf) throws IOException {
        if (id == ID_VIEW) clientView = reader.apply(buf);
    }

    @Override
    public void readOnServer(int id, PacketBuffer buf) {
        // Nothing comes from the client this way.
    }
}
