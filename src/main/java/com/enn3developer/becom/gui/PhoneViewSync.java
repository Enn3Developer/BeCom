package com.enn3developer.becom.gui;

import java.io.IOException;
import java.util.Arrays;
import java.util.function.Supplier;

import net.minecraft.network.PacketBuffer;

import com.cleanroommc.modularui.value.sync.SyncHandler;

import io.netty.buffer.Unpooled;

/**
 * Sends the phone screen's {@link PhoneView} from server to client whenever it changes. One way only: the client acts
 * through synced actions the server checks.
 */
public final class PhoneViewSync extends SyncHandler<PhoneViewSync> {

    private static final int ID_VIEW = 0;

    private final Supplier<PhoneView> serverView;
    private byte[] lastSent;
    private PhoneView clientView = PhoneView.EMPTY;

    /** @param serverView builds the view on the server; never called on the client */
    public PhoneViewSync(Supplier<PhoneView> serverView) {
        this.serverView = serverView;
    }

    public PhoneView view() {
        return clientView;
    }

    @Override
    public void detectAndSendChanges(boolean init) {
        // Containers also run change detection client side on slot clicks; only the server sends views.
        if (getSyncManager().isClient()) return;
        PhoneView view = serverView.get();
        PacketBuffer buf = new PacketBuffer(Unpooled.buffer());
        view.write(buf);
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        if (!init && Arrays.equals(bytes, lastSent)) return;
        lastSent = bytes;
        syncToClient(ID_VIEW, out -> out.writeBytes(bytes));
    }

    @Override
    public void readOnClient(int id, PacketBuffer buf) throws IOException {
        if (id == ID_VIEW) clientView = PhoneView.read(buf);
    }

    @Override
    public void readOnServer(int id, PacketBuffer buf) {
        // Nothing comes from the client this way.
    }
}
