package com.enn3developer.becom.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import org.junit.jupiter.api.Test;

/** DESIGN.md 15.5: a reward whose item isn't installed is left out, from a choice's options too. */
class QuestRewardsTest {

    private static final Set<String> INSTALLED = Set.of("becom:walkie_talkie", "enhancedlootbags:lootbag");

    private static NBTTagCompound item(String id) {
        NBTTagCompound item = new NBTTagCompound();
        item.setString("id", id);
        item.setInteger("Count", 1);
        return item;
    }

    private static NBTTagCompound reward(String type, String list, String... ids) {
        NBTTagCompound reward = new NBTTagCompound();
        reward.setString("rewardID", type);
        NBTTagList items = new NBTTagList();
        for (String id : ids) items.appendTag(item(id));
        if (list != null) reward.setTag(list, items);
        return reward;
    }

    private static NBTTagCompound quest(NBTTagCompound... rewards) {
        NBTTagCompound quest = new NBTTagCompound();
        NBTTagList list = new NBTTagList();
        for (NBTTagCompound reward : rewards) list.appendTag(reward);
        quest.setTag("rewards", list);
        return quest;
    }

    /** Each kept reward as its index, its type and its items' ids. */
    private static List<String> kept(NBTTagCompound quest) {
        List<String> kept = new ArrayList<>();
        NBTTagList rewards = QuestRewards.withInstalled(quest, INSTALLED::contains)
            .getTagList("rewards", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < rewards.tagCount(); i++) {
            NBTTagCompound reward = rewards.getCompoundTagAt(i);
            String list = reward.hasKey("choices") ? "choices" : "rewards";
            NBTTagList items = reward.getTagList(list, Constants.NBT.TAG_COMPOUND);
            StringBuilder ids = new StringBuilder();
            for (int j = 0; j < items.tagCount(); j++) ids.append(' ')
                .append(
                    items.getCompoundTagAt(j)
                        .getString("id"));
            kept.add(reward.getInteger("index") + " " + reward.getString("rewardID") + ids);
        }
        return kept;
    }

    @Test
    void aChoiceLosesTheOptionsThatArentInstalled() {
        NBTTagCompound quest = quest(
            reward("bq_standard:item", "rewards", "enhancedlootbags:lootbag"),
            reward("bq_standard:choice", "choices", "dreamcraft:CoinTechnician", "becom:walkie_talkie"));
        assertEquals(
            List.of("0 bq_standard:item enhancedlootbags:lootbag", "1 bq_standard:choice becom:walkie_talkie"),
            kept(quest));
    }

    @Test
    void aRewardWithNothingInstalledGoesAndTheRestAreNumberedAgain() {
        NBTTagCompound quest = quest(
            reward("bq_standard:item", "rewards", "dreamcraft:CoinTechnician"),
            reward("bq_standard:choice", "choices", "dreamcraft:CoinTechnician"),
            reward("bq_standard:command", null),
            reward("bq_standard:item", "rewards", "enhancedlootbags:lootbag", "dreamcraft:CoinTechnician"));
        assertEquals(List.of("0 bq_standard:command", "1 bq_standard:item enhancedlootbags:lootbag"), kept(quest));
    }
}
