package com.enn3developer.becom.quest.bq;

import java.util.regex.Pattern;

import net.minecraft.util.ResourceLocation;

import com.enn3developer.becom.BeCom;

import betterquesting.api2.client.gui.panels.content.PanelTextBox;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Each quest ends with a [guide] link to its guide page (DESIGN.md 15.5), which GuideNH turns into a link in the quest
 * book. Without GuideNH there is no guide to open, so the quest book shows the link's label alone.
 */
@SideOnly(Side.CLIENT)
public final class BqGuideTags {

    private static final Pattern TAG = Pattern.compile("\\[guide(?: page=[^\\] ]+)?](.*?)\\[/guide]", Pattern.DOTALL);

    private BqGuideTags() {}

    public static void register() {
        PanelTextBox.registerTextProcessor(new ResourceLocation(BeCom.MODID, "guide_tags"), BqGuideTags::strip);
    }

    static String strip(String text) {
        return text == null || !text.contains("[guide") ? text
            : TAG.matcher(text)
                .replaceAll("$1");
    }
}
