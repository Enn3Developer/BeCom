package com.enn3developer.becom.data;

import java.io.File;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.storage.MapStorage;
import net.minecraftforge.common.util.Constants;

import com.enn3developer.becom.logic.space.Satellites;

/**
 * The satellites in orbit, world-wide data kept with the Overworld: each one's dimension, when it went
 * up and when it comes down. A file of its own beside the phone registry, so a BeCom from before satellites never
 * reads it and never saves over it.
 */
public final class SatelliteSavedData extends WorldSavedData {

    public static final String NAME = "becom_satellites";
    public static final int FORMAT = 1;
    private static final String WHAT = "the satellites in orbit";

    private final Satellites satellites = new Satellites();
    private long savedVersion;
    private NBTTagCompound tooNewData;
    private boolean read;

    public SatelliteSavedData(String name) {
        super(name);
    }

    /** @throws IllegalStateException when the saved data is newer than this BeCom understands, or unreadable */
    public static SatelliteSavedData get(World world) {
        return load(
            world.mapStorage,
            world.getSaveHandler()
                .getMapFileFromName(NAME));
    }

    /** Loads the satellites from {@code storage}, whose file is {@code file}; {@link #get} for the Overworld. */
    static SatelliteSavedData load(MapStorage storage, File file) {
        SatelliteSavedData data = (SatelliteSavedData) storage.loadData(SatelliteSavedData.class, NAME);
        if (data == null) {
            data = new SatelliteSavedData(NAME);
            // Nothing on disk to read: no satellite has gone up yet.
            data.read = true;
            storage.setData(NAME, data);
        } else if (SavedDataFormat.unreadable(file != null && file.isFile(), data.read)) {
            data.tooNewData = new NBTTagCompound();
            throw SavedDataFormat.couldNotRead(WHAT, file);
        }
        if (data.tooNewData != null) {
            throw SavedDataFormat.newerThanUnderstood(WHAT, SavedDataFormat.versionOf(data.tooNewData), FORMAT);
        }
        return data;
    }

    public Satellites satellites() {
        return satellites;
    }

    /** Whether {@link #readFromNBT} ran; false for data vanilla failed to read. */
    public boolean wasRead() {
        return read;
    }

    public boolean isTooNew() {
        return tooNewData != null;
    }

    @Override
    public boolean isDirty() {
        if (tooNewData != null) return false;
        return super.isDirty() || satellites.version() != savedVersion;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        if (SavedDataFormat.tooNew(nbt, FORMAT)) {
            tooNewData = (NBTTagCompound) nbt.copy();
            read = true;
            return;
        }
        NBTTagList list = nbt.getTagList("satellites", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            satellites.add(
                new Satellites.Satellite(tag.getInteger("dim"), tag.getLong("launched"), tag.getLong("comesDown")));
        }
        savedVersion = satellites.version();
        // Last: anything thrown above leaves the data unread, and so never saved over the file.
        read = true;
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        if (tooNewData != null) {
            for (String key : tooNewData.func_150296_c()) nbt.setTag(key, tooNewData.getTag(key));
            return;
        }
        SavedDataFormat.write(nbt, FORMAT);
        NBTTagList list = new NBTTagList();
        for (Satellites.Satellite satellite : satellites.all()) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("dim", satellite.dimension());
            tag.setLong("launched", satellite.launched());
            tag.setLong("comesDown", satellite.comesDown());
            list.appendTag(tag);
        }
        nbt.setTag("satellites", list);
        savedVersion = satellites.version();
    }
}
