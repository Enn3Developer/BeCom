package com.enn3developer.becom.quest.bq;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;

import com.enn3developer.becom.quest.QuestTask;

import betterquesting.api.questing.IQuest;
import betterquesting.api2.client.gui.misc.IGuiRect;
import betterquesting.api2.client.gui.panels.IGuiPanel;
import betterquesting.api2.utils.ParticipantInfo;
import bq_standard.tasks.base.TaskBase;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * BeCom's own BetterQuesting task: done when BeCom says the thing happened, never by checking inventories. The
 * quest file names what it waits for; a name this BeCom doesn't know (from a newer one) is kept and never completes.
 */
public class TaskBeCom extends TaskBase {

    static final String KEY = "becomTask";

    private String kindName = QuestTask.FIRST_CALL.name();

    public QuestTask kind() {
        try {
            return QuestTask.valueOf(kindName);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public void setKind(QuestTask kind) {
        kindName = kind.name();
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        nbt.setString(KEY, kindName);
        return nbt;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        if (nbt.hasKey(KEY)) kindName = nbt.getString(KEY);
    }

    @Override
    public ResourceLocation getFactoryID() {
        return FactoryTaskBeCom.ID;
    }

    @Override
    public String getUnlocalisedName() {
        return "becom.task.name";
    }

    private String descriptionKey() {
        return "becom.task." + kindName.toLowerCase(Locale.ROOT);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IGuiPanel getTaskGui(IGuiRect rect, Map.Entry<UUID, IQuest> quest) {
        return new PanelTaskBeCom(rect, this, StatCollector.translateToLocal(descriptionKey()));
    }

    @Override
    @SideOnly(Side.CLIENT)
    public GuiScreen getTaskEditor(GuiScreen parent, Map.Entry<UUID, IQuest> quest) {
        return null;
    }

    @Override
    public void detect(ParticipantInfo participant, Map.Entry<UUID, IQuest> quest) {
        // Completed from BeCom's events, never by polling.
    }
}
