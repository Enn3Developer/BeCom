package com.enn3developer.becom.server;

import net.minecraft.util.StatCollector;

/**
 * The names GTNH Voice shows for BeCom's groups in its speaker list. VOICE.md V6: GTNH Voice sends a group's name to
 * clients as a plain string and draws it as it is, so BeCom can only translate it here, in the server's language. A
 * dedicated server's is always English; in single player the server shares the client's.
 */
// VOICE.md V6
final class GroupNames {

    private GroupNames() {}

    static String call() {
        return StatCollector.translateToLocal("becom.group.call");
    }

    static String radio(int channel) {
        return StatCollector.translateToLocalFormatted("becom.group.radio", channel);
    }
}
