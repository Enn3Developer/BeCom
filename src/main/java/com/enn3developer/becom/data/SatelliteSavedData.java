package com.enn3developer.becom.data;

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
    private static final String WHAT = "the satellites in orbit";

    private final Satellites satellites = new Satellites();
    private long savedVersion;

    public SatelliteSavedData(String name) {
        super(name);
    }

    public static SatelliteSavedData get(World world) {
        MapStorage storage = world.mapStorage;
        SatelliteSavedData data = (SatelliteSavedData) storage.loadData(SatelliteSavedData.class, NAME);
        if (data == null) {
            data = new SatelliteSavedData(NAME);
            storage.setData(NAME, data);
        }
        return data;
    }

    public Satellites satellites() {
        return satellites;
    }

    @Override
    public boolean isDirty() {
        return super.isDirty() || satellites.version() != savedVersion;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        NBTTagList list = nbt.getTagList("satellites", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            satellites.add(
                new Satellites.Satellite(tag.getInteger("dim"), tag.getLong("launched"), tag.getLong("comesDown")));
        }
        savedVersion = satellites.version();
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
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
