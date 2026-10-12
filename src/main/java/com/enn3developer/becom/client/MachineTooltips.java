package com.enn3developer.becom.client;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReferenceArray;

import com.enn3developer.becom.BeCom;
import com.enn3developer.becom.machine.BeComMachines;

import gregtech.api.metatileentity.implementations.MTETooltipMultiBlockBase;

/**
 * GT builds a multiblock's tooltip once and keeps it, per machine ID, until resources reload. BeCom's tooltips show
 * the server's tunables, so they are dropped whenever those change: on joining a server and on leaving it.
 */
public final class MachineTooltips {

    private static Field cache;
    private static boolean failed;

    private MachineTooltips() {}

    /** Forgets BeCom's cached multiblock tooltips, so the next one is built from the current values. */
    @SuppressWarnings("unchecked")
    public static void forget() {
        if (failed) return;
        try {
            if (cache == null) {
                cache = MTETooltipMultiBlockBase.class.getDeclaredField("tooltips");
                cache.setAccessible(true);
            }
            AtomicReferenceArray<Object> tooltips = (AtomicReferenceArray<Object>) cache.get(null);
            for (int id : BeComMachines.ids()
                .keySet()) {
                if (id < tooltips.length()) tooltips.set(id, null);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            // Only the tooltip goes stale; nothing else depends on it.
            failed = true;
            BeCom.LOG.warn("Couldn't refresh BeCom's machine tooltips; they may show the defaults", e);
        }
    }
}
