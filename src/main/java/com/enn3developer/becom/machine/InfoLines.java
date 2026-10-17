package com.enn3developer.becom.machine;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.Constants;

import com.enn3developer.becom.logic.net.BlockPos;
import com.enn3developer.becom.logic.net.CableTier;
import com.enn3developer.becom.logic.net.NetNode;
import com.enn3developer.becom.logic.net.NetworkModel;
import com.enn3developer.becom.server.BeComServer;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

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

    private record Line(String key, boolean detail, boolean problem, List<Arg> args) {

        String translate() {
            String colour = problem ? "§c" : "";
            if (args.isEmpty()) return colour + StatCollector.translateToLocal(key);
            Object[] values = new Object[args.size()];
            for (int i = 0; i < values.length; i++) values[i] = args.get(i)
                .resolve();
            return colour + StatCollector.translateToLocalFormatted(key, values);
        }
    }

    /** An argument the reader translates too, such as a level's or a portal's name. */
    public record Key(String key) {}

    private final List<Line> lines = new ArrayList<>();

    /**
     * The scanner's lines for one span from {@code from} to {@code to}: where it goes, how long it is, whether it's
     * blocked, and the cables strung on it, best first.
     */
    public static void span(InfoLines info, NetworkModel model, long from, long to) {
        info.detail(
            model.isBlocked(from, to) ? "becom.info.link_blocked" : "becom.info.link",
            BlockPos.toString(to),
            (int) Math.round(BlockPos.distance(from, to)));
        List<CableTier> cables = model.cables(from, to);
        if (cables.isEmpty()) {
            info.detail("becom.info.span_bare");
            return;
        }
        for (CableTier tier : CableTier.values()) {
            int count = 0;
            for (CableTier cable : cables) if (cable == tier) count++;
            if (count > 0) info.detail("becom.info.span_cables", count, cableName(tier));
        }
    }

    /** A cable tier's name, for the reader to translate. */
    public static Key cableName(CableTier tier) {
        return new Key("tile.becom." + com.enn3developer.becom.block.BlockPhoneCable.nameOf(tier) + ".name");
    }

    /** A line every reader shows. */
    public InfoLines add(String key, Object... args) {
        lines.add(new Line(key, false, false, args(args)));
        return this;
    }

    /** A line only the scanner shows, such as one per link, which would crowd WAILA's box. */
    public InfoLines detail(String key, Object... args) {
        lines.add(new Line(key, true, false, args(args)));
        return this;
    }

    /** What's wrong, if anything: every reader shows it in red, above the other lines. */
    public InfoLines problem(String key, Object... args) {
        lines.add(new Line(key, false, true, args(args)));
        return this;
    }

    /** The lines, problems first, otherwise in the order they were added. */
    private List<Line> ordered() {
        List<Line> ordered = new ArrayList<>(lines.size());
        for (Line line : lines) if (line.problem()) ordered.add(line);
        for (Line line : lines) if (!line.problem()) ordered.add(line);
        return ordered;
    }

    private static List<Arg> args(Object[] args) {
        List<Arg> list = new ArrayList<>(args.length);
        for (Object arg : args) {
            list.add(arg instanceof Key k ? new Arg(k.key(), true) : new Arg(String.valueOf(arg), false));
        }
        return list;
    }

    // How encode() joins a line's key and arguments, and marks an argument the reader translates too, and a problem.
    private static final char FIELD = '\u0001';
    private static final char TRANSLATED = '\u0002';
    private static final char PROBLEM = '\u0003';

    /** Every line, detail ones too, untranslated, for a screen that sends them to its viewer's client. */
    public List<String> encode() {
        List<String> encoded = new ArrayList<>(lines.size());
        for (Line line : ordered()) {
            StringBuilder text = new StringBuilder(line.problem() ? String.valueOf(PROBLEM) : "").append(line.key());
            for (Arg arg : line.args()) text.append(FIELD)
                .append(arg.key() ? String.valueOf(TRANSLATED) : "")
                .append(arg.text());
            encoded.add(text.toString());
        }
        return encoded;
    }

    /** Lines {@link #encode} made, joined by newlines, in this side's language. */
    public static List<String> decode(String joined) {
        List<String> text = new ArrayList<>();
        if (joined == null || joined.isEmpty()) return text;
        for (String encoded : joined.split("\n")) {
            boolean problem = !encoded.isEmpty() && encoded.charAt(0) == PROBLEM;
            if (problem) encoded = encoded.substring(1);
            String[] fields = encoded.split(String.valueOf(FIELD), -1);
            List<Arg> args = new ArrayList<>();
            for (int i = 1; i < fields.length; i++) {
                boolean key = !fields[i].isEmpty() && fields[i].charAt(0) == TRANSLATED;
                args.add(new Arg(key ? fields[i].substring(1) : fields[i], key));
            }
            text.add(new Line(fields[0], false, problem, args).translate());
        }
        return text;
    }

    /** Every line, in this side's language: the server's, for GT's scanner. */
    public List<String> translate() {
        List<String> text = new ArrayList<>(lines.size());
        for (Line line : ordered()) text.add(line.translate());
        return text;
    }

    public String[] translateArray() {
        return translate().toArray(new String[0]);
    }

    /** Writes the lines WAILA shows into the tag the server sends it. */
    public void write(NBTTagCompound tag) {
        NBTTagList list = new NBTTagList();
        for (Line line : ordered()) {
            if (line.detail()) continue;
            NBTTagCompound entry = new NBTTagCompound();
            entry.setString("k", line.key());
            if (line.problem()) entry.setBoolean("p", true);
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
            tip.add(new Line(entry.getString("k"), false, entry.getBoolean("p"), values).translate());
        }
    }

    /**
     * Its backhaul: each run of wire from the node at {@code pos} to the next working node, its span's
     * best cable, and how much of its KB/s calls take; a problem when it has none.
     */
    public static void backhaul(InfoLines info, BeComServer becom, int dimension, long pos, String key) {
        NetworkModel model = becom.network.model(dimension);
        NetNode node = model == null ? null : model.node(pos);
        List<com.enn3developer.becom.server.NetworkService.Run> runs = becom.network.runsFrom(dimension, pos);
        if (node == null || node.links()
            .isEmpty()) {
            info.problem("becom.info.no_backhaul");
            return;
        }
        // Runs start only at working nodes: an offline machine says so itself, and its wire may be fine.
        if (!becom.network.working(dimension, pos)) return;
        if (runs.isEmpty()) {
            info.problem("becom.info.backhaul_dead");
            return;
        }
        long first = node.links()
            .iterator()
            .next();
        CableTier best = null;
        for (CableTier cable : model.cables(pos, first))
            if (best == null || cable.ordinal() > best.ordinal()) best = cable;
        for (com.enn3developer.becom.server.NetworkService.Run run : runs) {
            NetNode far = model.node(run.to());
            if (run.rate() <= 0) {
                info.problem("becom.info.backhaul_too_long", BlockPos.toString(run.to()));
                continue;
            }
            info.add(
                key,
                best == null ? "-" : cableName(best),
                becom.calls.usedOn(run.link()) + becom.apps.dataOn(run.link()),
                run.rate(),
                new Key(
                    "becom.node." + (far == null ? "pylon"
                        : far.kind()
                            .name()
                            .toLowerCase(java.util.Locale.ROOT))),
                BlockPos.toString(run.to()));
        }
    }

    /** How many calls go through the machine on {@code tile} now, out of the {@code slots} it carries at once. */
    public static void calls(InfoLines info, IGregTechTileEntity tile, int slots) {
        BeComServer becom = BeComServer.get();
        if (becom == null || tile == null) return;
        int calls = becom.calls.callsAt(
            tile.getWorld().provider.dimensionId,
            BlockPos.pack(tile.getXCoord(), tile.getYCoord(), tile.getZCoord()));
        info.add("becom.info.calls", calls, slots);
        if (slots > 0 && calls >= slots) info.problem("becom.info.calls_full", slots);
    }
}
