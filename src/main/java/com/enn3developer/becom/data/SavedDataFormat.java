package com.enn3developer.becom.data;

import net.minecraft.nbt.NBTTagCompound;

/**
 * The format version every piece of BeCom saved data carries. Older data is upgraded as it loads;
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

    /**
     * Whether a data file that exists was never read: vanilla's {@code MapStorage.loadData} swallows a read error (a
     * file cut short by a crash mid-save, say) and hands back an empty object, which would be saved over the file on
     * the first change.
     */
    public static boolean unreadable(boolean fileExists, boolean read) {
        return fileExists && !read;
    }

    public static IllegalStateException couldNotRead(String what, java.io.File file) {
        return new IllegalStateException(
            "BeCom couldn't read " + what
                + " from "
                + file
                + ". Stopping instead of starting it empty and saving over the file: restore it from a backup, or"
                + " delete it to start with none.");
    }

    /**
     * Data in a format this BeCom knows, holding something it doesn't: a kind, a value or more of something than it
     * allows, which only a newer BeCom writes.
     */
    public static IllegalStateException notUnderstood(String what, String detail) {
        return new IllegalStateException(
            "BeCom found " + what
                + " holding "
                + detail
                + ", saved by a newer BeCom. Stopping instead of dropping it and saving over the file: update BeCom,"
                + " or restore a backup made with this version.");
    }

    /** Data no BeCom writes, in any version: the file was damaged, or edited by hand. */
    public static IllegalStateException damaged(String what, String detail) {
        return new IllegalStateException(
            "BeCom found " + what
                + " holding "
                + detail
                + ", which no BeCom writes: the file is damaged or was edited by hand. Stopping instead of saving over"
                + " it: restore a backup, or fix the file.");
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
