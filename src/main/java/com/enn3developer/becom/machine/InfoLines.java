package com.enn3developer.becom.machine;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.Constants;

/**
 * What a machine says about itself, kept as translation keys and their arguments so each reader translates it: GT's
 * scanner on the server, and WAILA on the looking player's client, in that player's own language.
 */
public final class InfoLines {

    private static final String TAG = "becomInfo";

    private record Arg(String text, boolean key) {

        String resolve() {
            return key ? StatCollector.translateToLocal(text) : text;
        }
    }

    private record Line(String key, boolean detail, List<Arg> args) {

        String translate() {
            if (args.isEmpty()) return StatCollector.translateToLocal(key);
            Object[] values = new Object[args.size()];
            for (int i = 0; i < values.length; i++) values[i] = args.get(i)
                .resolve();
            return StatCollector.translateToLocalFormatted(key, values);
        }
    }

    /** An argument the reader translates too, such as a level's or a portal's name. */
    public record Key(String key) {}

    private final List<Line> lines = new ArrayList<>();

    /** A line every reader shows. */
    public InfoLines add(String key, Object... args) {
        lines.add(new Line(key, false, args(args)));
        return this;
    }

    /** A line only the scanner shows, such as one per link, which would crowd WAILA's box. */
    public InfoLines detail(String key, Object... args) {
        lines.add(new Line(key, true, args(args)));
        return this;
    }

    private static List<Arg> args(Object[] args) {
        List<Arg> list = new ArrayList<>(args.length);
        for (Object arg : args) {
            list.add(arg instanceof Key k ? new Arg(k.key(), true) : new Arg(String.valueOf(arg), false));
        }
        return list;
    }

    /** Every line, in this side's language: the server's, for GT's scanner. */
    public List<String> translate() {
        List<String> text = new ArrayList<>(lines.size());
        for (Line line : lines) text.add(line.translate());
        return text;
    }

    public String[] translateArray() {
        return translate().toArray(new String[0]);
    }

    /** Writes the lines WAILA shows into the tag the server sends it. */
    public void write(NBTTagCompound tag) {
        NBTTagList list = new NBTTagList();
        for (Line line : lines) {
            if (line.detail()) continue;
            NBTTagCompound entry = new NBTTagCompound();
            entry.setString("k", line.key());
            NBTTagList args = new NBTTagList();
            for (Arg arg : line.args()) {
                NBTTagCompound value = new NBTTagCompound();
                value.setString(arg.key() ? "k" : "s", arg.text());
                args.appendTag(value);
            }
            entry.setTag("a", args);
            list.appendTag(entry);
        }
        tag.setTag(TAG, list);
    }

    /** Adds the lines the server wrote to WAILA's box, translated on this client. */
    public static void read(NBTTagCompound tag, List<String> tip) {
        if (tag == null || !tag.hasKey(TAG, Constants.NBT.TAG_LIST)) return;
        NBTTagList list = tag.getTagList(TAG, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            NBTTagList args = entry.getTagList("a", Constants.NBT.TAG_COMPOUND);
            List<Arg> values = new ArrayList<>(args.tagCount());
            for (int j = 0; j < args.tagCount(); j++) {
                NBTTagCompound value = args.getCompoundTagAt(j);
                values.add(
                    value.hasKey("k") ? new Arg(value.getString("k"), true) : new Arg(value.getString("s"), false));
            }
            tip.add(new Line(entry.getString("k"), false, values).translate());
        }
    }
}
