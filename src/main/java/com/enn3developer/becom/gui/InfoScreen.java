package com.enn3developer.becom.gui;

import java.util.function.Supplier;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.enn3developer.becom.machine.InfoLines;

/**
 * A machine's screen that shows what it says about itself (its {@link InfoLines}, scanner details too): what's wrong,
 * in red on top, then its state, calls and backhaul. For the ME Bridge and the Portal Relay, which have nothing to set.
 */
public final class InfoScreen {

    private static final int WIDTH = 240;
    private static final int HEIGHT = 130;

    // A machine says a dozen lines; whole lines past this are left out rather than cut.
    private static final int MAX_BYTES = 16 * 1024;

    private InfoScreen() {}

    /**
     * A machine's lines, encoded, sent to its screen when they change. Built on the server only: MUI2 also checks
     * synced
     * values on the client, where building them would reach into the integrated server's state from the wrong thread.
     */
    public static ViewSync<String> lines(Supplier<InfoLines> info) {
        return new ViewSync<>(
            () -> fit(
                info.get()
                    .encode()),
            InfoScreen::write,
            InfoScreen::read,
            "");
    }

    /** The lines joined, as many whole ones as fit. */
    public static String fit(java.util.List<String> lines) {
        StringBuilder out = new StringBuilder();
        int bytes = 0;
        for (String line : lines) {
            int size = line.getBytes(java.nio.charset.StandardCharsets.UTF_8).length + 1;
            if (bytes + size > MAX_BYTES) break;
            if (out.length() > 0) out.append('\n');
            out.append(line);
            bytes += size;
        }
        return out.toString();
    }

    private static void write(String s, net.minecraft.network.PacketBuffer buf) {
        byte[] bytes = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        buf.writeVarIntToBuffer(bytes.length);
        buf.writeBytes(bytes);
    }

    private static String read(net.minecraft.network.PacketBuffer buf) {
        byte[] bytes = new byte[Math.max(0, Math.min(buf.readVarIntFromBuffer(), MAX_BYTES))];
        buf.readBytes(bytes);
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    /** {@code info} runs on the server only; each client translates the lines it's sent. */
    public static ModularPanel build(String id, String title, Supplier<InfoLines> info, PanelSyncManager sync) {
        ViewSync<String> text = lines(info);
        sync.syncValue("becomInfo", text);
        ModularPanel panel = new ModularPanel(id).size(WIDTH, HEIGHT);
        Flow column = Flow.column()
            .sizeRel(1f)
            .padding(6, 6, 6, 6)
            .childPadding(4);
        column.child(
            IKey.str(title)
                .asWidget()
                .widthRel(1f)
                .height(10));
        column.child(
            IKey.dynamic(() -> String.join("\n", InfoLines.decode(text.view())))
                .asWidget()
                .widthRel(1f)
                .expanded());
        panel.child(column);
        return panel;
    }
}
