package com.enn3developer.becom.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraftforge.common.UsernameCache;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.enn3developer.becom.BeCom;
import com.enn3developer.becom.gui.machine.LineTerminalUi;
import com.enn3developer.becom.gui.paint.CanvasWidget;
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

    private LineTerminalScreen() {}

    public static ModularPanel build(MTELineTerminal terminal, PosGuiData data, PanelSyncManager sync) {
        ViewSync<LineTerminalView> viewSync = new ViewSync<>(
            () -> view(terminal),
            LineTerminalView::write,
            LineTerminalView::read,
            LineTerminalView.EMPTY);
        sync.syncValue("view", viewSync);
        CanvasWidget screen = new CanvasWidget(
            new LineTerminalUi(viewSync::view),
            LineTerminalUi.scale(BeCom.proxy.guiHeight()));
        return new ModularPanel("becom_line_terminal").size(screen.width(), screen.height())
            .background(IDrawable.EMPTY)
            .child(screen);
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
            if (device.length() > reach) {
                now = LineTerminalView.Now.TOO_FAR;
            } else if (!becom.network.takes(self, device)) {
                now = LineTerminalView.Now.NEARER;
            } else if (!served.contains(device)) {
                now = LineTerminalView.Now.NO_PORT;
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
                    with = others(becom, call, device.phone());
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
                    with,
                    device.phone() < 0 ? 0 : becom.apps.dataOn(becom.network.deviceLink(device.phone()))));
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

    /** Who else is in the call: the first one's name, and how many more. */
    private static String others(BeComServer becom, Call call, int phone) {
        List<String> others = new ArrayList<>();
        for (Call.Leg leg : call.activeLegs()) {
            if (leg.phone() == phone) continue;
            String fallback = becom.phones.name(leg.phone());
            others.add(leg.player() == null ? fallback : becom.calls.nameOf(leg.player(), fallback));
        }
        if (others.isEmpty()) return "-";
        return others.size() == 1 ? others.get(0) : others.get(0) + " +" + (others.size() - 1);
    }
}
