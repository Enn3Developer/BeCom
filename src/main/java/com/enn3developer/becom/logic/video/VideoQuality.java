package com.enn3developer.becom.logic.video;

import com.enn3developer.becom.logic.SignalLevel;
import com.enn3developer.becom.logic.Tunables;

/**
 * How each camera's picture looks at a level (DESIGN.md 10.1's table, 17): the front camera's height and frame rate,
 * the rear camera's size and frame rate. A frame rate of 0 means every frame. Nothing moves below Medium (D43), and the
 * rear camera needs High.
 */
public record VideoQuality(int frontHeight, int frontFps, int rearWidth, int rearHeight, int rearFps) {

    public static final VideoQuality NONE = new VideoQuality(0, 0, 0, 0, 0);

    public static VideoQuality of(Tunables t, SignalLevel level) {
        return switch (level) {
            case MEDIUM -> new VideoQuality(t.videoHeightMedium(), t.videoFpsMedium(), 0, 0, 0);
            case HIGH -> new VideoQuality(
                t.videoHeightHigh(),
                t.videoFpsHigh(),
                t.rearWidthHigh(),
                t.rearHeightHigh(),
                t.rearFpsHigh());
            case MAX -> new VideoQuality(
                t.videoHeightMax(),
                t.videoFpsMax(),
                t.rearWidthMax(),
                t.rearHeightMax(),
                t.rearFpsMax());
            default -> NONE;
        };
    }

    /** Whether the front camera's picture moves at all. */
    public boolean frontLive() {
        return frontHeight > 0;
    }

    /** Whether the rear camera works at all. */
    public boolean rear() {
        return rearWidth > 0 && rearHeight > 0;
    }

    /** The shortest gap between two rear pictures, in milliseconds; 0 for no limit. */
    public long rearGapMillis() {
        return rearFps <= 0 ? 0 : 1000L / rearFps;
    }
}
