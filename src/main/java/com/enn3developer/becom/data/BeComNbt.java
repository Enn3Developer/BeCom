package com.enn3developer.becom.data;

import net.minecraft.nbt.NBTTagCompound;

/**
 * BeCom's keys in a compound it shares with others: a GT machine's NBT, which GT writes into too, or an item stack's
 * tag, which IC2 keeps its charge in. They all start with {@code becom}, and {@link #KEY} holds their format version.
 * Whatever a newer BeCom wrote is kept as it was and written back, and the thing is left alone.
 */
public final class BeComNbt {

    public static final String KEY = "becomFormat";
    private static final String PREFIX = "becom";

    private BeComNbt() {}

    public static void write(NBTTagCompound nbt, int current) {
        nbt.setInteger(KEY, current);
    }

    /** Whether a newer BeCom wrote these keys. No key means the first format, from before there was one. */
    public static boolean tooNew(NBTTagCompound nbt, int current) {
        return nbt != null && nbt.hasKey(KEY) && nbt.getInteger(KEY) > current;
    }

    /** A copy of BeCom's keys alone, to write back untouched. */
    public static NBTTagCompound ours(NBTTagCompound nbt) {
        NBTTagCompound copy = new NBTTagCompound();
        for (String key : nbt.func_150296_c()) {
            if (key.startsWith(PREFIX)) copy.setTag(
                key,
                nbt.getTag(key)
                    .copy());
        }
        return copy;
    }

    /** Writes back keys {@link #ours} kept. */
    public static void restore(NBTTagCompound nbt, NBTTagCompound kept) {
        for (String key : kept.func_150296_c()) nbt.setTag(
            key,
            kept.getTag(key)
                .copy());
    }
}
