package com.enn3developer.becom.client;

import com.enn3developer.becom.logic.SignalLevel;
import com.enn3developer.becom.voice.CallGroup;
import com.enn3developer.becom.voice.RadioGroup;
import com.enn3developer.gtnhvoice.api.client.GtnhVoiceClient;
import com.enn3developer.gtnhvoice.api.client.IPlaybackFrame;
import com.enn3developer.gtnhvoice.api.client.IVoiceAddon;

/**
 * How BeCom audio sounds on the listener's client (DESIGN.md 4.3, 13.5): one playback chain with a branch per level
 * and kind of group, picking frames by the group that routed them and the level the server said this player hears
 * that source at. These are DESIGN.md's starting recipes, to be tuned by ear; Max is left clean, and radios stop at
 * Medium.
 */
public final class ClientAudio {

    /** The addon name GTNH Voice knows BeCom by; radio activations derive their wire id from it. */
    public static final String ADDON = "BeCom";

    private static IVoiceAddon addon;

    private ClientAudio() {}

    public static IVoiceAddon addon() {
        return addon;
    }

    public static void register() {
        addon = GtnhVoiceClient.addon(ADDON)
            .description("Better Communications: telephones, radios and satellites")
            .register();
        addon.audio()
            .playbackChain(
                chain -> chain.when(
                    frame -> callLevel(frame) == SignalLevel.LOW,
                    low -> low.bandPass(300, 3000)
                        .mulaw()
                        .downsampleHold(4)
                        .noise(0.03))
                    .when(
                        frame -> callLevel(frame) == SignalLevel.MEDIUM,
                        medium -> medium.bandPass(250, 4000)
                            .mulaw()
                            .noise(0.012))
                    .when(
                        frame -> callLevel(frame) == SignalLevel.HIGH,
                        high -> high.bandPass(150, 7000)
                            .noise(0.004))
                    .when(
                        frame -> radioLevel(frame) == SignalLevel.LOW,
                        low -> low.bandPass(400, 2800)
                            .softClip()
                            .mulaw()
                            .noise(0.05))
                    .when(
                        frame -> radioLevel(frame) == SignalLevel.MEDIUM,
                        medium -> medium.bandPass(300, 3400)
                            .softClip()
                            .noise(0.02)))
            .done();
    }

    private static SignalLevel callLevel(IPlaybackFrame frame) {
        if (!CallGroup.NAME.equals(frame.groupName())) return SignalLevel.NONE;
        return ClientLevels.get(frame.sourceId(), frame.groupName());
    }

    private static SignalLevel radioLevel(IPlaybackFrame frame) {
        String group = frame.groupName();
        if (group == null || !group.startsWith(RadioGroup.PREFIX)) return SignalLevel.NONE;
        return ClientLevels.radio(frame.sourceId(), group);
    }
}
