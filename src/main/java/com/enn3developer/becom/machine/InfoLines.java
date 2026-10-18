package com.enn3developer.becom.machine;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.Constants;

import com.enn3developer.becom.config.BeComConfig;
import com.enn3developer.becom.logic.Tunables;
import com.enn3developer.becom.logic.net.BlockPos;
import com.enn3developer.becom.logic.net.Buses;
import com.enn3developer.becom.logic.net.CableTier;
import com.enn3developer.becom.server.BeComServer;
import com.enn3developer.becom.server.NetworkService;

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

    /**
     * How a screen lays a line out, from the machine boards: a plain line; the status on top ({@code STATUS}) and what
     * goes on its right ({@code RIGHT}); a row with {@code label} on its left; a bar filled to {@code fraction}; grey
     * words added to the row before ({@code SUB}); or a warning, orange where a problem is red. Readers without a
     * layout
     * (WAILA, the scanner) show the line's text alone.
     */
    public record Shape(char kind, String label, double fraction) {

        public static final char PLAIN = 'p';
        public static final char STATUS = 's';
        public static final char RIGHT = 'r';
        public static final char BAR = 'b';
        public static final char SUB = 'u';
        public static final char WARN = 'w';
        public static final char LEVEL = 'l';
        public static final char SECTION = 'h';
        public static final char VALUE = 'v';
        public static final char GOOD = 'g';

        static final Shape NONE = new Shape(PLAIN, "", 0);
    }

    private record Line(String key, boolean detail, boolean problem, List<Arg> args, Shape shape) {

        Line(String key, boolean detail, boolean problem, List<Arg> args) {
            this(key, detail, problem, args, Shape.NONE);
        }

        Line shaped(Shape shape) {
            return new Line(key, detail, problem, args, shape);
        }

        /**
         * The line as a reader without a layout shows it, as on the Scanner board: the state with a coloured square,
         * then a grey label before each row's value, white, or green, red or a level's colour; grey words after.
         */
        String translate() {
            String text = text();
            char kind = shape.kind();
            if (kind == Shape.STATUS) {
                String colour = problem ? "\u00a7c"
                    : shape.fraction() < 0 ? "\u00a76"
                        : shape.fraction() >= 1 ? levelCode(shape.fraction() - 1) : "\u00a7a";
                return colour + "\u25a0 " + text;
            }
            if (kind == Shape.SUB || kind == Shape.RIGHT || kind == Shape.SECTION) return "\u00a77" + text;
            if (kind == Shape.WARN) return "\u00a76" + text;
            String label = shape.label()
                .isEmpty() ? "" : StatCollector.translateToLocal(shape.label());
            if (label.isEmpty()) return problem ? "\u00a7c" + text : text;
            String value = problem ? "\u00a7c"
                : kind == Shape.GOOD ? "\u00a7a"
                    : kind == Shape.VALUE || kind == Shape.LEVEL ? levelCode(shape.fraction()) : "\u00a7f";
            return "\u00a77" + label + " " + value + text;
        }

        /** A level's colour as a formatting code: orange below Low, as on the screens. */
        private static String levelCode(double ordinal) {
            return switch ((int) Math.round(ordinal)) {
                case 1 -> "\u00a76";
                case 2 -> "\u00a7e";
                case 3 -> "\u00a7a";
                case 4 -> "\u00a7b";
                default -> "\u00a76";
            };
        }

        String text() {
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

    /** One of eight compass points from a step east and south, as a lang key's last part: north is -z. */
    public static String direction(int dx, int dz) {
        if (dx == 0 && dz == 0) return "here";
        String[] names = { "east", "south_east", "south", "south_west", "west", "north_west", "north", "north_east" };
        int i = (int) Math.round(Math.atan2(dz, dx) / (Math.PI / 4));
        return names[(i + 8) % 8];
    }

    /** A cable's short name, "Fibre", for the reader to translate. */
    public static Key shortName(CableTier tier) {
        return new Key(
            "becom.ui.cable." + tier.name()
                .toLowerCase(Locale.ROOT));
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

    /**
     * A warning: shown like a problem, above the other lines, but orange: the machine works, not as well as it could.
     */
    public InfoLines warn(String key, Object... args) {
        lines.add(new Line(key, false, true, args(args), new Shape(Shape.WARN, "", 0)));
        return this;
    }

    private InfoLines shape(char kind, String label, double fraction) {
        if (lines.isEmpty()) return this;
        int last = lines.size() - 1;
        lines.set(
            last,
            lines.get(last)
                .shaped(new Shape(kind, label, fraction)));
        return this;
    }

    /** The last line is the screen's status, on top. */
    public InfoLines status() {
        return shape(Shape.STATUS, "", 0);
    }

    /** The last line is the status, orange: something's happening, such as a phone ringing. */
    public InfoLines statusBusy() {
        return shape(Shape.STATUS, "", -1);
    }

    /**
     * The state line: Online, or why it isn't, as the Scanner board splits it: not formed, switched off, or without
     * the power it needs.
     */
    public static void state(InfoLines info, boolean formed, boolean switchedOn, boolean working) {
        if (!formed) info.problem("becom.info.state_unformed")
            .status();
        else if (!switchedOn) info.problem("becom.info.state_off")
            .status();
        else if (!working) info.problem("becom.info.state_no_power")
            .status();
        else info.add("becom.info.online")
            .status();
    }

    /** The last line goes on the right of the status, such as the power it draws. */
    public InfoLines right() {
        return shape(Shape.RIGHT, "", 0);
    }

    /** The last line is a row of the screen, with {@code labelKey} on its left. */
    public InfoLines row(String labelKey) {
        return shape(Shape.PLAIN, labelKey, 0);
    }

    /** The last line is the status, after signal bars at {@code level}, in the level's colour. */
    public InfoLines status(com.enn3developer.becom.logic.SignalLevel level) {
        return shape(Shape.STATUS, "", level.ordinal() + 1);
    }

    /** The last line heads a section of rows, under a rule. */
    public InfoLines section() {
        return shape(Shape.SECTION, "", 0);
    }

    /** The last line is a row whose text is in {@code level}'s colour, orange below Low. */
    public InfoLines value(String labelKey, com.enn3developer.becom.logic.SignalLevel level) {
        return shape(Shape.VALUE, labelKey, level.ordinal());
    }

    /** The last line is a row whose text is green: all is well. */
    public InfoLines good(String labelKey) {
        return shape(Shape.GOOD, labelKey, 0);
    }

    /** The last line is a row of signal bars at {@code level}, its text in the level's colour. */
    public InfoLines level(String labelKey, com.enn3developer.becom.logic.SignalLevel level) {
        return shape(Shape.LEVEL, labelKey, level.ordinal());
    }

    /** The last line is a row with a bar filled to {@code fraction}, its text on the bar's right. */
    public InfoLines bar(String labelKey, double fraction) {
        return shape(Shape.BAR, labelKey, fraction);
    }

    /** The last line adds grey words to the row before it. */
    public InfoLines sub() {
        return shape(Shape.SUB, "", 0);
    }

    /** One line as a screen lays it out, in this side's language. */
    public record Row(char kind, String label, String text, double fraction, boolean problem) {}

    /** The lines, problems first, otherwise in the order they were added. */
    // The state goes first, then what's wrong, so a screen that cuts lines short keeps them; a problem that's a row of
    // a screen's layout stays in its place there.
    private List<Line> ordered() {
        List<Line> ordered = new ArrayList<>(lines.size());
        for (Line line : lines) if (line.shape()
            .kind() == Shape.STATUS) ordered.add(line);
        for (Line line : lines) if (line.problem() && line.shape()
            .equals(Shape.NONE)) ordered.add(line);
        for (Line line : lines) if (line.shape()
            .kind() != Shape.STATUS
            && (!line.problem() || !line.shape()
                .equals(Shape.NONE)))
            ordered.add(line);
        return ordered;
    }

    private static List<Arg> args(Object[] args) {
        List<Arg> list = new ArrayList<>(args.length);
        for (Object arg : args) {
            list.add(arg instanceof Key k ? new Arg(k.key(), true) : new Arg(String.valueOf(arg), false));
        }
        return list;
    }

    // How encode() joins a line's key and arguments, and marks an argument the reader translates too, a problem, and
    // a line's shape: SHAPE, its kind, its label key, SHAPE, its fraction, SHAPE, then the key.
    private static final char FIELD = '\u0001';
    private static final char TRANSLATED = '\u0002';
    private static final char PROBLEM = '\u0003';
    private static final char SHAPE = '\u0004';

    /** Every line, detail ones too, untranslated, for a screen that sends them to its viewer's client. */
    public List<String> encode() {
        List<String> encoded = new ArrayList<>(lines.size());
        for (Line line : ordered()) {
            StringBuilder text = new StringBuilder(line.problem() ? String.valueOf(PROBLEM) : "");
            Shape shape = line.shape();
            if (!shape.equals(Shape.NONE)) text.append(SHAPE)
                .append(shape.kind())
                .append(shape.label())
                .append(SHAPE)
                .append(shape.fraction())
                .append(SHAPE);
            text.append(line.key());
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
        for (Line line : lines(joined)) text.add(line.translate());
        return text;
    }

    /** Lines {@link #encode} made, joined by newlines, as a screen lays them out, in this side's language. */
    public static List<Row> rows(String joined) {
        List<Row> rows = new ArrayList<>();
        for (Line line : lines(joined)) {
            Shape shape = line.shape();
            String label = shape.label()
                .isEmpty() ? "" : StatCollector.translateToLocal(shape.label());
            rows.add(new Row(shape.kind(), label, line.text(), shape.fraction(), line.problem()));
        }
        return rows;
    }

    private static List<Line> lines(String joined) {
        List<Line> lines = new ArrayList<>();
        if (joined == null || joined.isEmpty()) return lines;
        for (String encoded : joined.split("\n")) {
            boolean problem = !encoded.isEmpty() && encoded.charAt(0) == PROBLEM;
            if (problem) encoded = encoded.substring(1);
            Shape shape = Shape.NONE;
            if (!encoded.isEmpty() && encoded.charAt(0) == SHAPE) {
                String[] parts = encoded.substring(1)
                    .split(String.valueOf(SHAPE), 3);
                if (parts.length == 3 && !parts[0].isEmpty()) {
                    double fraction;
                    try {
                        fraction = Double.parseDouble(parts[1]);
                    } catch (NumberFormatException e) {
                        fraction = 0;
                    }
                    shape = new Shape(parts[0].charAt(0), parts[0].substring(1), fraction);
                    encoded = parts[2];
                }
            }
            String[] fields = encoded.split(String.valueOf(FIELD), -1);
            List<Arg> args = new ArrayList<>();
            for (int i = 1; i < fields.length; i++) {
                boolean key = !fields[i].isEmpty() && fields[i].charAt(0) == TRANSLATED;
                args.add(new Arg(key ? fields[i].substring(1) : fields[i], key));
            }
            lines.add(new Line(fields[0], false, problem, args, shape));
        }
        return lines;
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
            if (!line.shape()
                .equals(Shape.NONE)) {
                entry.setString(
                    "s",
                    String.valueOf(
                        line.shape()
                            .kind()));
                entry.setString(
                    "l",
                    line.shape()
                        .label());
                entry.setDouble(
                    "f",
                    line.shape()
                        .fraction());
            }
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
            String kind = entry.getString("s");
            Shape shape = kind.isEmpty() ? Shape.NONE
                : new Shape(kind.charAt(0), entry.getString("l"), entry.getDouble("f"));
            tip.add(new Line(entry.getString("k"), false, entry.getBoolean("p"), values, shape).translate());
        }
    }

    /** How a gateway's board writes its backhaul. */
    public enum Backhaul {
        /** "Fibre, 10 of 256 KB/s": the ME Bridge, and the Portal Relay for its own side and for the far side. */
        RATE,
        /** "Fibre to a router": the Satellite Dish. */
        CABLE
    }

    /**
     * A gateway's backhaul: the bus of cable from the node at {@code pos} to a working router, as a row labelled
     * {@code label} written as {@code style} says; a problem when it's on none, or its bus carries nothing.
     */
    public static void backhaul(InfoLines info, BeComServer becom, int dimension, long pos, String label,
        Backhaul style) {
        Buses.Bus bus = becom.network.backhaulOf(dimension, pos);
        if (bus == null) {
            // The dish's cable goes into its Line Hatch; the others take it on any face.
            info.problem(style == Backhaul.CABLE ? "becom.info.no_backhaul_hatch" : "becom.info.no_backhaul");
            return;
        }
        Tunables t = BeComConfig.get();
        if (bus.tooLong()) {
            info.problem("becom.info.backhaul_too_long", cableName(bus.tier()), t.cableReach(bus.tier()));
            return;
        }
        List<Long> routers = becom.network.routersOn(dimension, bus);
        if (routers.isEmpty()) {
            info.problem("becom.info.backhaul_dead");
            return;
        }
        Object link = NetworkService.busLink(dimension, bus);
        Key name = new Key(
            "becom.ui.cable." + bus.tier()
                .name()
                .toLowerCase(Locale.ROOT));
        if (style == Backhaul.CABLE) {
            info.add("becom.info.backhaul_cable", name)
                .row(label);
            return;
        }
        info.add(
            "becom.info.backhaul_value",
            name,
            becom.calls.usedOn(link) + becom.apps.dataOn(link),
            t.cableRate(bus.tier()))
            .row(label);
    }

    /**
     * The buses of cable joining the machine at {@code pos}: a row each with its cable, its length and what calls and
     * data take of it, or why it carries nothing; a problem when no cable joins it.
     */
    public static void buses(InfoLines info, BeComServer becom, int dimension, long pos) {
        List<Buses.Bus> buses = new ArrayList<>(becom.network.busesAt(dimension, pos));
        if (buses.isEmpty()) {
            info.problem("becom.info.no_bus")
                .row("becom.info.row.bus");
            return;
        }
        buses.sort(java.util.Comparator.comparingLong(Buses.Bus::id));
        for (Buses.Bus bus : buses) bus(info, becom, dimension, bus);
    }

    /** One bus's row: its cable, its length out of what that carries over, and what calls take of it; or why not. */
    private static void bus(InfoLines info, BeComServer becom, int dimension, Buses.Bus bus) {
        Tunables t = BeComConfig.get();
        int limit = t.cableReach(bus.tier());
        if (bus.tooLong()) {
            info.problem("becom.info.bus_too_long", cableName(bus.tier()), limit)
                .row("becom.info.row.bus");
        } else if (!becom.network.carries(dimension, bus)) {
            info.problem("becom.info.bus_no_router", cableName(bus.tier()), bus.cables(), limit)
                .row("becom.info.row.bus");
        } else {
            Object link = NetworkService.busLink(dimension, bus);
            info.add(
                "becom.info.bus",
                cableName(bus.tier()),
                bus.cables(),
                limit,
                becom.calls.usedOn(link) + becom.apps.dataOn(link),
                t.cableRate(bus.tier()))
                .row("becom.info.row.bus");
        }
    }

    /** The row of the bus a cable block is on, as {@link #buses} gives each of a machine's; a problem for none. */
    public static void cableBus(InfoLines info, BeComServer becom, int dimension, long cable) {
        Buses.Bus bus = becom.network.busOfCable(dimension, cable);
        if (bus == null) info.problem("becom.info.no_bus")
            .row("becom.info.row.bus");
        else bus(info, becom, dimension, bus);
    }

    /**
     * A cable block's lines, as WAILA shows them: whether its bus carries, and why not; the bus's row; and what's on
     * it.
     */
    public static void cable(InfoLines info, BeComServer becom, int dimension, long pos) {
        Buses.Bus bus = becom.network.busOfCable(dimension, pos);
        if (bus == null) {
            // Placed this tick: its bus is worked out on the next.
            info.problem("becom.info.no_bus")
                .status();
            return;
        }
        if (bus.tooLong()) info.problem("becom.info.state_too_long")
            .status();
        else if (!becom.network.carries(dimension, bus)) info.problem("becom.info.state_no_router")
            .status();
        else info.add("becom.info.state_carries")
            .status();
        bus(info, becom, dimension, bus);
        info.add(
            "becom.info.on_bus",
            bus.ends(Buses.Kind.PHONE)
                .size(),
            bus.ends(Buses.Kind.ROUTER)
                .size(),
            bus.ends(Buses.Kind.GATEWAY)
                .size())
            .row("becom.info.row.on_bus");
    }

    /**
     * How many calls go through the machine on {@code tile} now, out of the {@code slots} it carries at once; whether
     * they're all taken, for the caller to say so in its board's words.
     */
    public static boolean calls(InfoLines info, IGregTechTileEntity tile, int slots) {
        BeComServer becom = BeComServer.get();
        if (becom == null || tile == null) return false;
        int calls = becom.calls.callsAt(
            tile.getWorld().provider.dimensionId,
            BlockPos.pack(tile.getXCoord(), tile.getYCoord(), tile.getZCoord()));
        info.add("becom.info.of_slots", calls, slots)
            .bar("becom.info.row.calls", slots <= 0 ? 0 : Math.min(1, calls / (double) slots));
        return slots > 0 && calls >= slots;
    }
}
