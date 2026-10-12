package com.enn3developer.becom.data;

import java.io.File;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.chunk.storage.IChunkLoader;
import net.minecraft.world.storage.IPlayerFileData;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldInfo;

/** Vanilla's own MapStorage over a temporary folder, to load saved data the way a world does. */
final class StorageFixture {

    final File dir;

    StorageFixture(File dir) {
        this.dir = dir;
    }

    File file(String name) {
        return new File(dir, name + ".dat");
    }

    MapStorage storage() {
        return new MapStorage(new ISaveHandler() {

            @Override
            public WorldInfo loadWorldInfo() {
                return null;
            }

            @Override
            public void checkSessionLock() {}

            @Override
            public IChunkLoader getChunkLoader(WorldProvider provider) {
                return null;
            }

            @Override
            public void saveWorldInfoWithPlayer(WorldInfo info, NBTTagCompound player) {}

            @Override
            public void saveWorldInfo(WorldInfo info) {}

            @Override
            public IPlayerFileData getSaveHandler() {
                return null;
            }

            @Override
            public void flush() {}

            @Override
            public File getWorldDirectory() {
                return dir;
            }

            @Override
            public File getMapFileFromName(String name) {
                return file(name);
            }

            @Override
            public String getWorldDirectoryName() {
                return "test";
            }
        });
    }
}
