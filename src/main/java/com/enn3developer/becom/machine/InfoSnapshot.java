package com.enn3developer.becom.machine;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import net.minecraft.nbt.NBTTagCompound;

import com.enn3developer.becom.BeCom;

/**
 * A machine's lines for WAILA. WAILA asks for them on the network thread, where BeCom's networks, AE2's grids and the
 * world can't be read safely, so it gets the lines the server thread last worked out, and its asking queues fresh
 * ones for the next tick. WAILA asks again every 250 ms while a player looks, so the lines fill in at once.
 */
public final class InfoSnapshot {

    private static final Queue<InfoSnapshot> WANTED = new ConcurrentLinkedQueue<>();
    private static boolean warned;

    private final Supplier<InfoLines> source;
    private final AtomicBoolean queued = new AtomicBoolean();
    private volatile InfoLines lines;

    public InfoSnapshot(Supplier<InfoLines> source) {
        this.source = source;
    }

    /** On WAILA's network thread: writes the last lines and asks the server thread for fresh ones. */
    public void write(NBTTagCompound tag) {
        if (queued.compareAndSet(false, true)) WANTED.add(this);
        InfoLines current = lines;
        if (current != null) current.write(tag);
    }

    /**
     * On the server thread, once a tick: works out the lines WAILA asked for before this tick. A request that comes in
     * meanwhile, even for a machine just worked out, waits for the next tick, so a flood of them can't hold this one.
     */
    public static void refreshWanted() {
        // Each machine is queued at most once until its flag clears below, so this drain ends.
        List<InfoSnapshot> wanted = new ArrayList<>();
        for (InfoSnapshot next = WANTED.poll(); next != null; next = WANTED.poll()) wanted.add(next);
        for (InfoSnapshot snapshot : wanted) {
            // Cleared first, so a request that comes in while this one is worked out queues the next.
            snapshot.queued.set(false);
            try {
                snapshot.lines = snapshot.source.get();
            } catch (RuntimeException e) {
                // A machine broken since it was looked at, say. WAILA is a nicety; the tick goes on.
                if (!warned) BeCom.LOG.warn("BeCom couldn't work out a machine's WAILA lines", e);
                warned = true;
            }
        }
    }
}
