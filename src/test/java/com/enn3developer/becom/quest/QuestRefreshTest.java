package com.enn3developer.becom.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.common.util.Constants;

import org.junit.jupiter.api.Test;

/**
 * DEVIATIONS.md X42: a BeCom quest a world already holds takes the definition BeCom ships, and keeps its players'
 * progress where it still means the same. Quests and progress are built as BetterQuesting writes them.
 */
class QuestRefreshTest {

    private static final String ALICE = "a1a1a1a1-0000-4000-8000-000000000001";
    private static final String BOB = "b0b0b0b0-0000-4000-8000-000000000002";
    private static final String CAROL = "c0c0c0c0-0000-4000-8000-000000000003";

    /** Two Portal Relays to hold, then BeCom's own task. */
    private static NBTTagCompound portalQuest(int relays, String coinId) {
        NBTTagCompound quest = new NBTTagCompound();
        NBTTagCompound properties = new NBTTagCompound();
        NBTTagCompound bq = new NBTTagCompound();
        bq.setString("name", "Through the Portal");
        properties.setTag("betterquesting", bq);
        quest.setTag("properties", properties);
        NBTTagList tasks = new NBTTagList();
        NBTTagCompound retrieval = new NBTTagCompound();
        retrieval.setString("taskID", "bq_standard:retrieval");
        retrieval.setInteger("index", 0);
        NBTTagList required = new NBTTagList();
        required.appendTag(item("gregtech:gt.blockmachines", relays));
        retrieval.setTag("requiredItems", required);
        tasks.appendTag(retrieval);
        NBTTagCompound becom = new NBTTagCompound();
        becom.setString("taskID", "becom:event");
        becom.setInteger("index", 1);
        becom.setString("becomTask", "PORTAL_RELAY_PAIR");
        tasks.appendTag(becom);
        quest.setTag("tasks", tasks);
        NBTTagList rewards = new NBTTagList();
        NBTTagCompound coins = new NBTTagCompound();
        coins.setString("rewardID", "bq_standard:item");
        coins.setInteger("index", 0);
        NBTTagList items = new NBTTagList();
        items.appendTag(item(coinId, 25));
        coins.setTag("rewards", items);
        rewards.appendTag(coins);
        quest.setTag("rewards", rewards);
        quest.setTag("preRequisites", new NBTTagList());
        return quest;
    }

    private static NBTTagCompound item(String id, int count) {
        NBTTagCompound item = new NBTTagCompound();
        item.setString("id", id);
        item.setInteger("Count", count);
        item.setInteger("Damage", 0);
        return item;
    }

    /** Alice completed the quest and claimed it; Bob has done the BeCom task only. */
    private static NBTTagCompound progress() {
        NBTTagCompound progress = new NBTTagCompound();
        NBTTagList completed = new NBTTagList();
        NBTTagCompound alice = new NBTTagCompound();
        alice.setString("uuid", ALICE);
        alice.setBoolean("claimed", true);
        alice.setLong("timestamp", 1234L);
        completed.appendTag(alice);
        progress.setTag("completed", completed);
        NBTTagList tasks = new NBTTagList();
        tasks.appendTag(taskProgress("bq_standard:retrieval", 0, ALICE));
        tasks.appendTag(taskProgress("becom:event", 1, ALICE, BOB));
        progress.setTag("tasks", tasks);
        return progress;
    }

    private static NBTTagCompound taskProgress(String taskId, int index, String... done) {
        NBTTagCompound task = new NBTTagCompound();
        task.setString("taskID", taskId);
        task.setInteger("index", index);
        NBTTagList users = new NBTTagList();
        for (String user : done) users.appendTag(new NBTTagString(user));
        task.setTag("completeUsers", users);
        return task;
    }

    /** The indexes of the tasks whose progress is kept. */
    private static List<Integer> keptTasks(NBTTagCompound progress) {
        List<Integer> kept = new ArrayList<>();
        NBTTagList tasks = progress.getTagList("tasks", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < tasks.tagCount(); i++) kept.add(
            tasks.getCompoundTagAt(i)
                .getInteger("index"));
        return kept;
    }

    @Test
    void anUnchangedQuestIsLeftAlone() {
        assertFalse(
            QuestRefresh
                .changed(portalQuest(2, "dreamcraft:CoinTechnician"), portalQuest(2, "dreamcraft:CoinTechnician")));
    }

    @Test
    void aChangedTaskStartsOverAndTheOthersKeepTheirProgress() {
        NBTTagCompound stored = portalQuest(1, "dreamcraft:CoinTechnician");
        NBTTagCompound shipped = portalQuest(2, "dreamcraft:CoinTechnician");
        assertTrue(QuestRefresh.changed(stored, shipped));
        NBTTagCompound kept = QuestRefresh.progressFor(stored, shipped, progress());
        // Holding one relay no longer does: that task starts over, for everyone. BeCom's own task is as it was.
        assertEquals(List.of(1), keptTasks(kept));
        assertEquals(
            progress().getTagList("tasks", Constants.NBT.TAG_COMPOUND)
                .getCompoundTagAt(1),
            kept.getTagList("tasks", Constants.NBT.TAG_COMPOUND)
                .getCompoundTagAt(0));
    }

    @Test
    void aChangedRewardKeepsEveryonesProgress() {
        NBTTagCompound stored = portalQuest(2, "dreamcraft:CoinTechnician");
        NBTTagCompound shipped = portalQuest(2, "dreamcraft:CoinTechnicianI");
        assertTrue(QuestRefresh.changed(stored, shipped));
        assertEquals(progress(), QuestRefresh.progressFor(stored, shipped, progress()));
    }

    @Test
    void aCompletedQuestStaysCompleteAndClaimed() {
        // Every task changes: the retrieval asks for more, and BeCom's own task becomes a retrieval too.
        NBTTagCompound stored = portalQuest(1, "dreamcraft:CoinTechnician");
        NBTTagCompound shipped = portalQuest(3, "dreamcraft:CoinTechnician");
        NBTTagCompound becom = shipped.getTagList("tasks", Constants.NBT.TAG_COMPOUND)
            .getCompoundTagAt(1);
        becom.setString("taskID", "bq_standard:retrieval");
        becom.removeTag("becomTask");
        NBTTagCompound kept = QuestRefresh.progressFor(stored, shipped, progress());
        assertEquals(List.of(), keptTasks(kept));
        assertEquals(progress().getTag("completed"), kept.getTag("completed"), "Alice's completion and claim stay");
    }

    @Test
    void everyoneWhoseTaskStartsOverIsFoundWhereverTheTaskKeepsThem() {
        // Carol has counted one of the relays the retrieval task asks for, under her UUID, and finished nothing. The
        // task now asks for more, so Alice's completion of it and Carol's count go; Bob only did BeCom's own task.
        NBTTagCompound progress = progress();
        NBTTagCompound counting = progress.getTagList("tasks", Constants.NBT.TAG_COMPOUND)
            .getCompoundTagAt(0);
        NBTTagCompound carol = new NBTTagCompound();
        carol.setString("uuid", CAROL);
        carol.setTag("value", new NBTTagIntArray(new int[] { 1 }));
        NBTTagList users = new NBTTagList();
        users.appendTag(carol);
        counting.setTag("userProgress", users);
        assertEquals(
            Set.of(UUID.fromString(ALICE), UUID.fromString(CAROL)),
            QuestRefresh.losingProgress(
                portalQuest(1, "dreamcraft:CoinTechnician"),
                portalQuest(2, "dreamcraft:CoinTechnician"),
                progress));
    }

    @Test
    void aChangedRewardTakesNobodysProgress() {
        assertEquals(
            Set.of(),
            QuestRefresh.losingProgress(
                portalQuest(2, "dreamcraft:CoinTechnician"),
                portalQuest(2, "dreamcraft:CoinTechnicianI"),
                progress()));
    }

    @Test
    void taskProgressWithoutAnIndexIsDropped() {
        NBTTagCompound stored = portalQuest(2, "dreamcraft:CoinTechnician");
        NBTTagCompound shipped = portalQuest(2, "dreamcraft:CoinTechnicianI");
        NBTTagCompound progress = progress();
        progress.getTagList("tasks", Constants.NBT.TAG_COMPOUND)
            .getCompoundTagAt(1)
            .removeTag("index");
        assertEquals(List.of(0), keptTasks(QuestRefresh.progressFor(stored, shipped, progress)));
    }
}
