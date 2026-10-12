package com.enn3developer.becom.data;

import net.minecraft.nbt.NBTTagCompound;

/**
 * The format version every piece of BeCom saved data carries (DESIGN.md 14.2). Older data is upgraded as it loads;
 * data newer than this BeCom understands is left untouched on disk and stops the server.
 * <p>
 * The check can't throw from {@code readFromNBT}: vanilla's {@code MapStorage.loadData} catches it, keeps the
 * half-read object and saves over the file later. So readers record the problem with {@link #check} and the code that
 * loaded the data throws once {@code loadData} has returned.
 */
public final class SavedDataFormat {

    public static final String KEY = "formatVersion";

    private SavedDataFormat() {}

    /** The version {@code nbt} was written with; data from before versions existed counts as version 1. */
    public static int versionOf(NBTTagCompound nbt) {
        return nbt.hasKey(KEY) ? nbt.getInteger(KEY) : 1;
    }

    /** True when {@code nbt} is newer than {@code current}, so it must not be read or overwritten. */
    public static boolean tooNew(NBTTagCompound nbt, int current) {
        return versionOf(nbt) > current;
    }

    public static void write(NBTTagCompound nbt, int current) {
        nbt.setInteger(KEY, current);
    }

    public static IllegalStateException newerThanUnderstood(String what, int found, int current) {
        return new IllegalStateException(
            "BeCom found " + what
                + " saved in format "
                + found
                + ", newer than this BeCom understands ("
                + current
                + "). Stopping instead of overwriting it: update BeCom, or restore a backup made with this version.");
    }
}
