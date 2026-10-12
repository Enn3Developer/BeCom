package com.enn3developer.becom.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.util.StatCollector;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

import com.enn3developer.becom.config.BeComConfig;
import com.enn3developer.becom.item.ItemSignalAnalyzer;
import com.enn3developer.becom.logic.radio.RadioModel;
import com.enn3developer.becom.packet.AnalyzerPacket;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * The Signal Analyzer's overlay (DESIGN.md 6.6, 11.4, D35): while the player holds one, the server's latest reading in
 * the top-left corner: SNR and level, the antenna serving them, the biggest loss, and every loss term.
 */
public final class AnalyzerOverlay {

    private static final int MARGIN = 4;
    private static final int LINE = 10;
    private static final int BACKGROUND = 0x90000000;
    private static final int TEXT = 0xFFE0E0E0;
    // A reading older than a few refreshes is stale: the server stopped sending, or the player just picked it up.
    private static final int STALE_REFRESHES = 3;

    private static volatile AnalyzerPacket reading;
    private static volatile long readAt;

    public static void set(AnalyzerPacket packet) {
        reading = packet;
        readAt = System.currentTimeMillis();
    }

    public static void clear() {
        reading = null;
    }

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.gameSettings.showDebugInfo || !ItemSignalAnalyzer.is(mc.thePlayer.getHeldItem()))
            return;
        List<String> lines = lines();
        FontRenderer font = mc.fontRenderer;
        int width = 0;
        for (String line : lines) width = Math.max(width, font.getStringWidth(line));
        Gui.drawRect(MARGIN - 2, MARGIN - 2, MARGIN + width + 2, MARGIN + lines.size() * LINE, BACKGROUND);
        for (int i = 0; i < lines.size(); i++) font.drawStringWithShadow(lines.get(i), MARGIN, MARGIN + i * LINE, TEXT);
    }

    private static List<String> lines() {
        List<String> lines = new ArrayList<>();
        lines.add(StatCollector.translateToLocal("item.becom.signal_analyzer.name"));
        AnalyzerPacket r = reading;
        long staleAfter = (long) BeComConfig.get()
            .analyzerRefreshSeconds() * 1000 * STALE_REFRESHES;
        if (r == null || System.currentTimeMillis() - readAt > staleAfter) {
            lines.add(StatCollector.translateToLocal("becom.analyzer.reading"));
            return lines;
        }
        if (!r.served()) {
            lines.add(StatCollector.translateToLocal("becom.analyzer.none"));
            return lines;
        }
        RadioModel.Losses losses = r.losses();
        lines.add(
            StatCollector.translateToLocalFormatted(
                "becom.analyzer.snr",
                Math.round(losses.snr()),
                StatCollector.translateToLocal(
                    "becom.level." + r.level()
                        .name()
                        .toLowerCase(Locale.ROOT))));
        lines.add(StatCollector.translateToLocalFormatted("becom.analyzer.antenna", r.tier(), r.x(), r.y(), r.z()));
        lines.add(
            StatCollector.translateToLocalFormatted(
                "becom.analyzer.biggest",
                StatCollector.translateToLocal(
                    "becom.loss." + losses.biggest()
                        .name()
                        .toLowerCase(Locale.ROOT))));
        lines.add(
            StatCollector.translateToLocalFormatted(
                "becom.analyzer.terms",
                Math.round(losses.budget()),
                Math.round(losses.distance()),
                Math.round(losses.terrain()),
                Math.round(losses.coverA() + losses.coverB()),
                Math.round(losses.weather()),
                Math.round(losses.noise())));
        return lines;
    }
}
