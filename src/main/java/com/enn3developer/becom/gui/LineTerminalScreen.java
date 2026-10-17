package com.enn3developer.becom.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import net.minecraft.util.StatCollector;
import net.minecraftforge.common.UsernameCache;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.enn3developer.becom.logic.Tunables;
import com.enn3developer.becom.logic.call.Call;
import com.enn3developer.becom.logic.net.BlockPos;
import com.enn3developer.becom.logic.net.CableTier;
import com.enn3developer.becom.logic.net.NetNode;
import com.enn3developer.becom.logic.net.NetworkModel;
import com.enn3developer.becom.logic.net.NodeRef;
import com.enn3developer.becom.logic.phone.PhoneRecord;
import com.enn3developer.becom.machine.MTELineTerminal;
import com.enn3developer.becom.server.BeComServer;
import com.enn3developer.becom.server.NetworkService;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * The Line Terminal's screen: its state and owner, its uplink and what calls take of it, and a row per port, with each
 * device on its cable, its run, and what it's doing. Devices it can't serve follow, with why.
 */
public final class LineTerminalScreen {

    private static final int WIDTH = 276;
    private static final int HEIGHT = 182;
    private static final int ROWS = 9;
    private static final int ROW_HEIGHT = 11;
    // The table's columns: #, device, number, cable, run, now.
    private static final int[] COLUMNS = { 14, 62, 40, 42, 40, 62 };

    private LineTerminalScreen() {}

    public static ModularPanel build(MTELineTerminal terminal, PosGuiData data, PanelSyncManager sync) {
        ViewSync<LineTerminalView> viewSync = new ViewSync<>(
            () -> view(terminal),
            LineTerminalView::write,
            LineTerminalView::read,
            LineTerminalView.EMPTY);
        sync.syncValue("view", viewSync);
        Supplier<LineTerminalView> view = viewSync::view;

        ModularPanel panel = new ModularPanel("becom_line_terminal").size(WIDTH, HEIGHT);
        Flow column = Flow.column()
            .sizeRel(1f)
            .padding(6, 6, 6, 6)
            .childPadding(2);
        column.child(line(() -> title(view.get())));
        column.child(line(() -> uplink(view.get())));
        column.child(line(() -> usage(view.get())));
        Flow header = Flow.row()
            .widthRel(1f)
            .height(ROW_HEIGHT);
        String[] heads = { "#", "becom.terminal.col.device", "becom.terminal.col.number", "becom.terminal.col.cable",
            "becom.terminal.col.run", "becom.terminal.col.now" };
        for (int c = 0; c < COLUMNS.length; c++) {
            String head = heads[c];
            header.child(
                IKey.dynamic(() -> "§7" + (head.startsWith("becom.") ? StatCollector.translateToLocal(head) : head))
                    .asWidget()
                    .size(COLUMNS[c], ROW_HEIGHT));
        }
        column.child(header);
        IntSupplier size = () -> rows(view.get());
        int[] page = { 0 };
        for (int r = 0; r < ROWS; r++) {
            int row = r;
            Flow cells = Flow.row()
                .widthRel(1f)
                .height(ROW_HEIGHT);
            for (int c = 0; c < COLUMNS.length; c++) {
                int col = c;
                cells.child(
                    IKey.dynamic(() -> cell(view.get(), page[0] * ROWS + row, col))
                        .asWidget()
                        .size(COLUMNS[c], ROW_HEIGHT));
            }
            column.child(cells);
        }
        column.child(pager(page, size));
        column.child(
            IKey.lang("becom.terminal.hint")
                .asWidget()
                .widthRel(1f)
                .height(20));
        panel.child(column);
        return panel;
    }

    private static com.cleanroommc.modularui.widget.Widget<?> line(Supplier<String> text) {
        return IKey.dynamic(text::get)
            .asWidget()
            .widthRel(1f)
            .height(10);
    }

    private static String title(LineTerminalView view) {
        return StatCollector.translateToLocalFormatted(
            "becom.terminal.title",
            CableTier.byOrdinal(view.tier())
                .voltage(),
            StatCollector.translateToLocal(view.working() ? "becom.info.online" : "becom.info.offline"),
            view.owner()
                .isEmpty() ? "-" : view.owner());
    }

    private static String uplink(LineTerminalView view) {
        if (view.uplinks()
            .isEmpty()) return "§c" + StatCollector.translateToLocal("becom.terminal.no_uplink");
        LineTerminalView.Uplink u = view.uplinks()
            .get(0);
        return StatCollector.translateToLocalFormatted(
            "becom.terminal.uplink",
            cables(u.cables()),
            kindName(u.kind()),
            u.x() + ", " + u.y() + ", " + u.z(),
            u.distance());
    }

    /** "2× Phone Cable, Coaxial Cable", or a dash for a bare span. */
    private static String cables(int[] counts) {
        List<String> parts = new ArrayList<>();
        for (int tier = 0; tier < counts.length; tier++) {
            if (counts[tier] <= 0) continue;
            String name = cableName(tier);
            parts.add(counts[tier] > 1 ? counts[tier] + "× " + name : name);
        }
        return parts.isEmpty() ? "-" : String.join(", ", parts);
    }

    private static String cableName(int tier) {
        return StatCollector.translateToLocal(
            "tile.becom." + com.enn3developer.becom.block.BlockPhoneCable.nameOf(CableTier.byOrdinal(tier)) + ".name");
    }

    private static String kindName(int kind) {
        if (kind < 0) return StatCollector.translateToLocal("becom.node.pylon");
        return StatCollector.translateToLocal(
            "becom.node." + com.enn3developer.becom.logic.net.NodeKind.values()[kind].name()
                .toLowerCase(Locale.ROOT));
    }

    private static String usage(LineTerminalView view) {
        if (view.uplinks()
            .isEmpty()) return "";
        LineTerminalView.Uplink u = view.uplinks()
            .get(0);
        if (!view.working()) return "§c" + StatCollector.translateToLocal("becom.terminal.offline");
        if (u.rate() < 0) return "§c" + StatCollector.translateToLocal("becom.terminal.uplink_down");
        if (u.rate() == 0) return "§c" + StatCollector.translateToLocal("becom.terminal.run_too_long");
        int calls = 0;
        for (LineTerminalView.Device d : view.devices()) if (d.now() == LineTerminalView.Now.CALL) calls++;
        int used = u.used() + u.data();
        String colour = used >= u.rate() ? "§c" : used * 4 >= u.rate() * 3 ? "§e" : "§a";
        String line = StatCollector
            .translateToLocalFormatted("becom.terminal.line", colour + used + "§r", u.rate(), calls);
        return u.data() <= 0 ? line : line + StatCollector.translateToLocalFormatted("becom.terminal.data", u.data());
    }

    /** Every served device, the free ports after them, then the devices it can't serve. */
    private static int rows(LineTerminalView view) {
        int served = 0;
        for (LineTerminalView.Device d : view.devices()) if (d.now()
            .served()) served++;
        return Math.max(view.ports(), served) + view.devices()
            .size() - served;
    }

    private static String cell(LineTerminalView view, int row, int col) {
        List<LineTerminalView.Device> served = new ArrayList<>(), unserved = new ArrayList<>();
        for (LineTerminalView.Device d : view.devices()) (d.now()
            .served() ? served : unserved).add(d);
        int ports = Math.max(view.ports(), served.size());
        LineTerminalView.Device device;
        if (row < served.size()) device = served.get(row);
        else if (row < ports) device = null;
        else if (row - ports < unserved.size()) device = unserved.get(row - ports);
        else return "";
        boolean port = row < ports;
        if (col == 0) return port ? "§7" + (row + 1) : "§8-";
        if (device == null) return col == 1 ? "§8" + StatCollector.translateToLocal("becom.terminal.free") : "";
        return switch (col) {
            case 1 -> device.kind() < 0 ? "?"
                : StatCollector.translateToLocal(
                    "becom.phone.kind." + com.enn3developer.becom.logic.phone.PhoneKind.values()[device.kind()].name()
                        .toLowerCase(Locale.ROOT));
            case 2 -> device.number() < 0 ? "-" : String.valueOf(device.number());
            case 3 -> StatCollector.translateToLocal(
                "becom.terminal.cable." + CableTier.byOrdinal(device.cable())
                    .name()
                    .toLowerCase(Locale.ROOT));
            case 4 -> (device.run() > device.reach() ? "§c" : "") + device.run() + "/" + reach(device.reach());
            case 5 -> now(device);
            default -> "";
        };
    }

    private static String reach(int blocks) {
        return blocks >= 1000 && blocks % 1024 == 0 ? (blocks / 1024) + "k" : String.valueOf(blocks);
    }

    private static String now(LineTerminalView.Device device) {
        return switch (device.now()) {
            case IDLE -> "§7" + StatCollector.translateToLocal("becom.terminal.now.idle");
            case CALL -> "§a" + StatCollector.translateToLocalFormatted("becom.terminal.now.call", device.with());
            case RINGING -> "§e" + StatCollector.translateToLocal("becom.terminal.now.ringing");
            case NO_PORT -> "§c" + StatCollector.translateToLocal("becom.terminal.now.no_port");
            case TOO_FAR -> "§c" + StatCollector.translateToLocal("becom.terminal.now.too_far");
            case NEARER -> "§8" + StatCollector.translateToLocal("becom.terminal.now.nearer");
        };
    }

    private static Flow pager(int[] page, IntSupplier size) {
        Flow pager = Flow.row()
            .coverChildren()
            .childPadding(4);
        pager.child(
            new ButtonWidget<>().size(20, 12)
                .overlay(IKey.str("<"))
                .onMousePressed(button -> {
                    if (page[0] > 0) page[0]--;
                    return true;
                }));
        pager.child(IKey.dynamic(() -> {
            int pages = Math.max(1, (size.getAsInt() + ROWS - 1) / ROWS);
            if (page[0] >= pages) page[0] = pages - 1;
            return (page[0] + 1) + "/" + pages;
        })
            .asWidget()
            .size(30, 12));
        pager.child(
            new ButtonWidget<>().size(20, 12)
                .overlay(IKey.str(">"))
                .onMousePressed(button -> {
                    if ((page[0] + 1) * ROWS < size.getAsInt()) page[0]++;
                    return true;
                }));
        return pager;
    }

    /** Server side: what the terminal shows. */
    public static LineTerminalView view(MTELineTerminal terminal) {
        BeComServer becom = BeComServer.get();
        IGregTechTileEntity tile = terminal.getBaseMetaTileEntity();
        if (becom == null || tile == null) return LineTerminalView.EMPTY;
        Tunables t = becom.tunables;
        NodeRef self = new NodeRef(
            tile.getWorld().provider.dimensionId,
            BlockPos.pack(tile.getXCoord(), tile.getYCoord(), tile.getZCoord()));
        NetworkModel model = becom.network.model(self.dimension());
        NetNode node = model == null ? null : model.node(self.pos());
        List<LineTerminalView.Uplink> uplinks = new ArrayList<>();
        if (node != null) {
            List<NetworkService.Run> runs = becom.network.runsFrom(self.dimension(), self.pos());
            for (long other : node.links()) {
                int[] cables = new int[CableTier.values().length];
                for (CableTier cable : model.cables(self.pos(), other)) cables[cable.ordinal()]++;
                // The span leads to a pylon or a machine; the run it starts goes on to the next working node.
                NetworkService.Run run = runs.isEmpty() ? null : runs.get(0);
                long end = run == null ? other : run.to();
                NetNode far = model.node(end);
                uplinks.add(
                    new LineTerminalView.Uplink(
                        far == null || far.kind() == com.enn3developer.becom.logic.net.NodeKind.PYLON ? -1
                            : far.kind()
                                .ordinal(),
                        BlockPos.x(end),
                        BlockPos.y(end),
                        BlockPos.z(end),
                        (int) Math.round(run == null ? BlockPos.distance(self.pos(), other) : run.length()),
                        cables,
                        // -1: no run at all, from a span down, an unformed pylon or a dead far end.
                        run == null ? -1 : run.rate(),
                        run == null ? 0 : becom.calls.usedOn(run.link()),
                        run == null ? 0 : becom.apps.dataOn(run.link())));
            }
        }
        List<NetworkService.Device> all = becom.network.devicesOf(self.dimension(), self.pos());
        List<NetworkService.Device> served = becom.network.served(self, all);
        List<LineTerminalView.Device> devices = new ArrayList<>();
        for (NetworkService.Device device : all) {
            PhoneRecord record = device.phone() < 0 ? null : becom.phones.record(device.phone());
            int reach = t.cableReach(device.cable());
            LineTerminalView.Now now;
            String with = "";
            if (!served.contains(device)) {
                now = device.length() > reach ? LineTerminalView.Now.TOO_FAR : LineTerminalView.Now.NO_PORT;
            } else if (device.phone() >= 0 && !self.equals(becom.network.terminalOf(device.phone()))) {
                now = LineTerminalView.Now.NEARER;
            } else {
                Call call = device.phone() < 0 ? null
                    : becom.calls.book()
                        .callOfPhone(device.phone());
                Call.Leg leg = call == null ? null : call.legOf(device.phone());
                if (leg == null || leg.state() == Call.Leg.State.LEFT) {
                    now = LineTerminalView.Now.IDLE;
                } else if (leg.state() == Call.Leg.State.RINGING) {
                    now = LineTerminalView.Now.RINGING;
                } else {
                    now = LineTerminalView.Now.CALL;
                    with = others(call, device.phone());
                }
            }
            devices.add(
                new LineTerminalView.Device(
                    record == null ? -1
                        : record.kind()
                            .ordinal(),
                    device.phone(),
                    device.cable()
                        .ordinal(),
                    device.length(),
                    reach,
                    now,
                    with));
        }
        // Served ones first, keeping the scan's nearest-first order within each part.
        devices.sort(
            java.util.Comparator.comparing(
                d -> !d.now()
                    .served()));
        UUID owner = tile.getOwnerUuid();
        String ownerName = owner == null ? null : UsernameCache.getLastKnownUsername(owner);
        return new LineTerminalView(
            terminal.tier()
                .ordinal(),
            terminal.isWorking(),
            ownerName == null ? "" : ownerName,
            uplinks,
            t.terminalPorts(terminal.tier()),
            devices);
    }

    /** The other phones in the call: the first one's number, and how many more. */
    private static String others(Call call, int phone) {
        List<Integer> others = new ArrayList<>();
        for (Call.Leg leg : call.activeLegs()) if (leg.phone() != phone) others.add(leg.phone());
        if (others.isEmpty()) return "-";
        return others.size() == 1 ? String.valueOf(others.get(0)) : others.get(0) + " +" + (others.size() - 1);
    }
}
