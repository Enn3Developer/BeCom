package com.enn3developer.becom.quest.bq;

import net.minecraft.client.Minecraft;
import net.minecraft.util.EnumChatFormatting;

import betterquesting.api.api.QuestingAPI;
import betterquesting.api.utils.RenderUtils;
import betterquesting.api2.client.gui.misc.GuiAlign;
import betterquesting.api2.client.gui.misc.GuiTransform;
import betterquesting.api2.client.gui.misc.IGuiRect;
import betterquesting.api2.client.gui.panels.CanvasMinimum;
import betterquesting.api2.client.gui.panels.content.PanelTextBox;
import betterquesting.api2.client.gui.themes.presets.PresetColor;
import betterquesting.api2.utils.QuestTranslation;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * A BeCom task in the quest screen: what it waits for and whether it's done. A CanvasMinimum, like bq_standard's own
 * task panels, so the quest screen's frame around it fits the text instead of the whole task column.
 */
@SideOnly(Side.CLIENT)
class PanelTaskBeCom extends CanvasMinimum {

    // BetterQuesting's own line height for task text.
    private static final int LINE_HEIGHT = 12;

    private final IGuiRect initialRect;
    private final TaskBeCom task;
    private final String description;

    PanelTaskBeCom(IGuiRect rect, TaskBeCom task, String description) {
        super(rect);
        this.initialRect = rect;
        this.task = task;
        this.description = description;
    }

    @Override
    public void initPanel() {
        super.initPanel();
        int width = initialRect.getWidth();
        Minecraft mc = Minecraft.getMinecraft();
        boolean done = task.isComplete(QuestingAPI.getQuestingUUID(mc.thePlayer));
        String text = description + "\n"
            + (done ? EnumChatFormatting.GREEN + QuestTranslation.translate("betterquesting.tooltip.complete")
                : EnumChatFormatting.RED + QuestTranslation.translate("betterquesting.tooltip.incomplete"));
        int lines = RenderUtils.splitString(text, width, mc.fontRenderer)
            .size();
        addPanel(
            new PanelTextBox(new GuiTransform(GuiAlign.TOP_LEFT, 0, 0, width, lines * LINE_HEIGHT, 0), text)
                .setColor(PresetColor.TEXT_MAIN.getColor()));
        recalcSizes();
    }
}
