package com.enn3developer.becom.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.enn3developer.becom.data.SavedDataFormat;

/** DESIGN.md 14.2: everything BeCom saves carries a format version, the Telephone's number included. */
class TileTelephoneTest {

    @BeforeAll
    static void register() {
        try {
            TileEntity.addMapping(TileTelephone.class, "becom.telephone");
        } catch (IllegalArgumentException e) {
            // Already mapped by another test in this JVM.
        }
    }

    @Test
    void savesItsNumberWithAFormatVersion() {
        TileTelephone tile = new TileTelephone();
        tile.setNumber(123_456);
        NBTTagCompound nbt = new NBTTagCompound();
        tile.writeToNBT(nbt);
        assertEquals(TileTelephone.FORMAT, SavedDataFormat.versionOf(nbt));
        assertTrue(nbt.hasKey(SavedDataFormat.KEY));
        TileTelephone back = new TileTelephone();
        back.readFromNBT(nbt);
        assertEquals(123_456, back.number());
        assertFalse(back.isTooNew());
    }

    @Test
    void aNewerTelephoneIsKeptAsItWasAndLeftAlone() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setString("id", "becom.telephone");
        nbt.setInteger(SavedDataFormat.KEY, TileTelephone.FORMAT + 1);
        nbt.setInteger("number", 654_321);
        nbt.setString("futureField", "hologram");
        TileTelephone tile = new TileTelephone();
        tile.readFromNBT(nbt);
        assertTrue(tile.isTooNew());
        assertEquals(-1, tile.number());
        NBTTagCompound written = new NBTTagCompound();
        tile.writeToNBT(written);
        assertEquals("hologram", written.getString("futureField"));
        assertEquals(654_321, written.getInteger("number"));
        assertEquals(TileTelephone.FORMAT + 1, SavedDataFormat.versionOf(written));
    }

    @Test
    void aNewerTelephoneMovedElsewhereSavesWhereItIsNow() {
        // AE2's spatial storage moves the same tile object and sets its coordinates, without reading it again.
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setString("id", "becom.telephone");
        nbt.setInteger("x", 1);
        nbt.setInteger("y", 64);
        nbt.setInteger("z", 1);
        nbt.setInteger(SavedDataFormat.KEY, TileTelephone.FORMAT + 1);
        TileTelephone tile = new TileTelephone();
        tile.readFromNBT(nbt);
        tile.xCoord = 100;
        NBTTagCompound written = new NBTTagCompound();
        tile.writeToNBT(written);
        assertEquals(100, written.getInteger("x"));
        assertEquals(TileTelephone.FORMAT + 1, SavedDataFormat.versionOf(written));
    }

    @Test
    void readingCurrentDataAgainForgetsNewerData() {
        TileTelephone tile = new TileTelephone();
        NBTTagCompound newer = new NBTTagCompound();
        newer.setString("id", "becom.telephone");
        newer.setInteger(SavedDataFormat.KEY, TileTelephone.FORMAT + 1);
        tile.readFromNBT(newer);
        NBTTagCompound current = new NBTTagCompound();
        current.setString("id", "becom.telephone");
        current.setInteger("number", 222_222);
        tile.readFromNBT(current);
        assertFalse(tile.isTooNew());
        assertEquals(222_222, tile.number());
    }
}
