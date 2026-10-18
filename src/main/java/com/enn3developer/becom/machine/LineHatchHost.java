package com.enn3developer.becom.machine;

import com.enn3developer.becom.logic.net.Buses;

/**
 * A multiblock buses join through its Line Hatches: an Exchange or an antenna, as a router, or a Satellite Dish, as a
 * gateway.
 */
public interface LineHatchHost {

    /** What it is to the buses its Line Hatches join. */
    Buses.Kind busKind();

    /** Whether its structure is formed, so its Line Hatches join it. */
    boolean formed();

    /** Whether it works now, which lights its Line Hatches. */
    boolean isWorking();

    /** Its Line Hatches, as its structure checks find them. */
    LineHatches lineHatches();
}
