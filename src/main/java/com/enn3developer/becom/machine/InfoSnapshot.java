package com.enn3developer.becom.machine;

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

    /** On the server thread, once a tick: works out the lines WAILA asked for since the last. */
    public static void refreshWanted() {
        for (InfoSnapshot wanted = WANTED.poll(); wanted != null; wanted = WANTED.poll()) {
            // Cleared first, so a request that comes in while this one is worked out queues the next.
            wanted.queued.set(false);
            try {
                wanted.lines = wanted.source.get();
            } catch (RuntimeException e) {
                // A machine broken since it was looked at, say. WAILA is a nicety; the tick goes on.
                if (!warned) BeCom.LOG.warn("BeCom couldn't work out a machine's WAILA lines", e);
                warned = true;
            }
        }
    }
}
