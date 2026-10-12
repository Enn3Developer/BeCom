package com.enn3developer.becom.quest.bq;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import betterquesting.api.questing.tasks.ITask;
import betterquesting.api2.registry.IFactoryData;

/** Makes {@link TaskBeCom}s for BetterQuesting, under the task ID BeCom's quest files use. */
public final class FactoryTaskBeCom implements IFactoryData<ITask, NBTTagCompound> {

    public static final ResourceLocation ID = new ResourceLocation("becom", "event");
    public static final FactoryTaskBeCom INSTANCE = new FactoryTaskBeCom();

    private FactoryTaskBeCom() {}

    @Override
    public ResourceLocation getRegistryName() {
        return ID;
    }

    @Override
    public TaskBeCom createNew() {
        return new TaskBeCom();
    }

    @Override
    public TaskBeCom loadFromData(NBTTagCompound nbt) {
        TaskBeCom task = new TaskBeCom();
        task.readFromNBT(nbt);
        return task;
    }
}
