package com.enn3developer.becom.quest;

import java.util.function.Predicate;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

/**
 * A reward whose item isn't installed is left out. Works on a quest as BetterQuesting saves it, without
 * BetterQuesting's classes, so it can be tested on its own.
 */
public final class QuestRewards {

    private QuestRewards() {}

    /**
     * Drops the items of {@code quest}'s rewards that aren't installed, from an item reward's list and from a choice's
     * options alike, and any reward left with none. Rewards without items, a command say, stay as they are.
     */
    public static NBTTagCompound withInstalled(NBTTagCompound quest, Predicate<String> installed) {
        NBTTagList rewards = quest.getTagList("rewards", Constants.NBT.TAG_COMPOUND);
        NBTTagList kept = new NBTTagList();
        for (int i = 0; i < rewards.tagCount(); i++) {
            NBTTagCompound reward = rewards.getCompoundTagAt(i);
            String list = reward.hasKey("choices") ? "choices" : "rewards";
            NBTTagList items = reward.getTagList(list, Constants.NBT.TAG_COMPOUND);
            if (items.tagCount() == 0) {
                reward.setInteger("index", kept.tagCount());
                kept.appendTag(reward);
                continue;
            }
            NBTTagList present = new NBTTagList();
            for (int j = 0; j < items.tagCount(); j++) {
                NBTTagCompound item = items.getCompoundTagAt(j);
                if (installed.test(item.getString("id"))) present.appendTag(item);
            }
            if (present.tagCount() == 0) continue;
            reward.setTag(list, present);
            reward.setInteger("index", kept.tagCount());
            kept.appendTag(reward);
        }
        quest.setTag("rewards", kept);
        return quest;
    }
}
