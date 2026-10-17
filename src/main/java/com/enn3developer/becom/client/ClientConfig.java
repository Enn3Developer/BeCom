package com.enn3developer.becom.client;

import java.io.File;
import java.util.Locale;

import net.minecraftforge.common.config.Configuration;

/**
 * BeCom's client config: what a player chooses for their own screen, in a file of its own beside the
 * server's. The keys live in Minecraft's controls screen; this holds which corner the video window sits in.
 */
public final class ClientConfig {

    /** A corner of the screen. */
    public enum Corner {

        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT;

        public boolean right() {
            return this == TOP_RIGHT || this == BOTTOM_RIGHT;
        }

        public boolean bottom() {
            return this == BOTTOM_LEFT || this == BOTTOM_RIGHT;
        }
    }

    private static final String VIDEO = "video";
    // Top right: the hotbar, the chat and the signal bars are at the bottom, and the incoming-call notice there only
    // shows while a phone rings, never during a call.
    private static final Corner DEFAULT_CORNER = Corner.TOP_RIGHT;

    private static Corner videoCorner = DEFAULT_CORNER;

    private ClientConfig() {}

    public static Corner videoCorner() {
        return videoCorner;
    }

    public static void load(File file) {
        Configuration config = new Configuration(file);
        config.load();
        String[] names = new String[Corner.values().length];
        for (int i = 0; i < names.length; i++) names[i] = Corner.values()[i].name()
            .toLowerCase(Locale.ROOT);
        String corner = config.getString(
            "videoCorner",
            VIDEO,
            DEFAULT_CORNER.name()
                .toLowerCase(Locale.ROOT),
            "Which corner of the screen the video window sits in during a video call.",
            names);
        try {
            videoCorner = Corner.valueOf(corner.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            videoCorner = DEFAULT_CORNER;
        }
        if (config.hasChanged()) config.save();
    }
}
