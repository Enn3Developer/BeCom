package com.enn3developer.becom.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.Test;

/** DESIGN.md 14.2 in compounds BeCom shares with GT and IC2: a format version, and newer keys kept as they were. */
class BeComNbtTest {

    @Test
    void noVersionIsTheFirstFormatAndAHigherOneIsTooNew() {
        NBTTagCompound nbt = new NBTTagCompound();
        assertFalse(BeComNbt.tooNew(nbt, 1));
        assertFalse(BeComNbt.tooNew(null, 1));
        BeComNbt.write(nbt, 1);
        assertFalse(BeComNbt.tooNew(nbt, 1));
        nbt.setInteger(BeComNbt.KEY, 2);
        assertTrue(BeComNbt.tooNew(nbt, 1));
    }

    @Test
    void keepsOnlyBeComsKeysAndWritesThemBackUntouched() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setInteger(BeComNbt.KEY, 7);
        nbt.setString("becomSomethingNew", "x");
        nbt.setInteger("mID", 25004);
        NBTTagCompound kept = BeComNbt.ours(nbt);
        assertFalse(kept.hasKey("mID"));
        NBTTagCompound saved = new NBTTagCompound();
        saved.setInteger("mID", 25004);
        BeComNbt.restore(saved, kept);
        assertEquals(7, saved.getInteger(BeComNbt.KEY));
        assertEquals("x", saved.getString("becomSomethingNew"));
        assertEquals(25004, saved.getInteger("mID"));
    }
}
