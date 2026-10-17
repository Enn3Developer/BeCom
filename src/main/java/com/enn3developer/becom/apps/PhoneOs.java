package com.enn3developer.becom.apps;

import java.io.IOException;
import java.io.InputStream;

import net.minecraft.item.ItemStack;

import com.enn3developer.becom.BeCom;

import li.cil.oc.api.Driver;
import li.cil.oc.api.Items;

/**
 * BeCom OS's pieces OpenComputers needs: the EEPROM that boots a phone, and the BeCom Card's driver and tools disk.
 * Registered once at init, on both sides.
 */
public final class PhoneOs {

    private static ItemStack eeprom;

    private PhoneOs() {}

    public static void init() {
        byte[] bios = resource("/assets/becom/lua/bios.lua");
        if (bios == null) {
            BeCom.LOG.error("BeCom OS's EEPROM code is missing from the jar: phone apps won't run");
        } else {
            eeprom = Items.registerEEPROM("EEPROM (BeCom OS)", bios, null, true);
        }
        Driver.add(new BecomCardDriver());
        Items.registerFloppy(
            "becom",
            11,
            () -> li.cil.oc.api.FileSystem.fromClass(BeCom.class, BeCom.MODID, "lua/tools"),
            true);
    }

    /** A copy of the phone's EEPROM, or null when its code was missing. */
    static ItemStack eeprom() {
        return eeprom == null ? null : eeprom.copy();
    }

    private static byte[] resource(String path) {
        try (InputStream in = PhoneOs.class.getResourceAsStream(path)) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }
}
