package com.enn3developer.becom.quest;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.common.util.Constants;

/**
 * Brings a BeCom quest that a world already holds up to the definition BeCom ships, keeping its players' progress.
 * Works on quests as BetterQuesting writes them, a definition
 * from {@code IQuest.writeToNBT} and progress from {@code writeProgressToNBT}, without BetterQuesting's classes, so it
 * can be tested on its own.
 */
public final class QuestRefresh {

    private QuestRefresh() {}

    /** Whether a stored definition differs from the shipped one, both as BetterQuesting writes them. */
    public static boolean changed(NBTTagCompound stored, NBTTagCompound shipped) {
        return !stored.equals(shipped);
    }

    /**
     * The progress for a quest that now holds {@code shipped}'s definition instead of {@code stored}'s: every player's
     * completion and claimed reward as they were, and each task's progress where the task at that index has the same
     * kind and requirements in both. The other tasks start over.
     */
    public static NBTTagCompound progressFor(NBTTagCompound stored, NBTTagCompound shipped, NBTTagCompound progress) {
        Map<Integer, NBTTagCompound> before = tasksByIndex(stored);
        Map<Integer, NBTTagCompound> after = tasksByIndex(shipped);
        NBTTagList tasks = progress.getTagList("tasks", Constants.NBT.TAG_COMPOUND);
        NBTTagList kept = new NBTTagList();
        for (int i = 0; i < tasks.tagCount(); i++) {
            NBTTagCompound task = tasks.getCompoundTagAt(i);
            if (kept(task, before, after)) kept.appendTag(task.copy());
        }
        NBTTagCompound result = (NBTTagCompound) progress.copy();
        result.setTag("tasks", kept);
        return result;
    }

    /**
     * Everyone whose progress file BetterQuesting has to write again once their quest takes {@code shipped}'s
     * definition. BetterQuesting saves a player's progress to their own file only once something marks them, and reads
     * every file back at the next start, matching each task's progress by index and kind. A file can hold progress the
     * book doesn't, for a task an older definition had at an index, which a newer task there may match again. So when
     * any task changed, everyone with progress in the quest is marked, wherever their task keeps them; when only its
     * rewards or properties did, nobody is, since every task's progress stays and the files hold nothing new to match.
     */
    public static Set<UUID> toMark(NBTTagCompound stored, NBTTagCompound shipped, NBTTagCompound progress) {
        Set<UUID> players = new HashSet<>();
        if (!tasksByIndex(stored).equals(tasksByIndex(shipped))) collect(progress, players);
        return players;
    }

    /** Whether a task's progress stays: the task at its index is defined just as before. */
    private static boolean kept(NBTTagCompound task, Map<Integer, NBTTagCompound> before,
        Map<Integer, NBTTagCompound> after) {
        // BetterQuesting matches a task's progress to its task by this index, and drops progress without one.
        if (!task.hasKey("index", Constants.NBT.TAG_ANY_NUMERIC)) return false;
        NBTTagCompound was = before.get(task.getInteger("index"));
        return was != null && was.equals(after.get(task.getInteger("index")));
    }

    private static void collect(NBTBase tag, Set<UUID> players) {
        if (tag instanceof NBTTagCompound compound) {
            for (String key : compound.func_150296_c()) collect(compound.getTag(key), players);
        } else if (tag instanceof NBTTagList list) {
            NBTTagList left = (NBTTagList) list.copy();
            while (left.tagCount() > 0) collect(left.removeTag(0), players);
        } else if (tag instanceof NBTTagString string) {
            try {
                players.add(UUID.fromString(string.func_150285_a_()));
            } catch (IllegalArgumentException notAPlayer) {
                // A task's kind, or another string a task keeps.
            }
        }
    }

    /** A definition's tasks by index, each with its kind ({@code taskID}) and requirements. */
    private static Map<Integer, NBTTagCompound> tasksByIndex(NBTTagCompound quest) {
        Map<Integer, NBTTagCompound> byIndex = new HashMap<>();
        NBTTagList tasks = quest.getTagList("tasks", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < tasks.tagCount(); i++) {
            NBTTagCompound task = tasks.getCompoundTagAt(i);
            if (task.hasKey("index", Constants.NBT.TAG_ANY_NUMERIC)) byIndex.put(task.getInteger("index"), task);
        }
        return byIndex;
    }
}
