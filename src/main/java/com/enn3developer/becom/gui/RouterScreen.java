package com.enn3developer.becom.gui;

import java.util.ArrayList;
import java.util.List;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.enn3developer.becom.BeCom;
import com.enn3developer.becom.gui.machine.RouterUi;
import com.enn3developer.becom.gui.paint.CanvasWidget;
import com.enn3developer.becom.logic.Tunables;
import com.enn3developer.becom.logic.call.Call;
import com.enn3developer.becom.logic.net.BlockPos;
import com.enn3developer.becom.logic.net.Buses;
import com.enn3developer.becom.logic.net.NetNode;
import com.enn3developer.becom.logic.net.NetworkModel;
import com.enn3developer.becom.logic.net.NodeRef;
import com.enn3developer.becom.logic.phone.PhoneRecord;
import com.enn3developer.becom.machine.MTERouter;
import com.enn3developer.becom.server.BeComServer;
import com.enn3developer.becom.server.NetworkService;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * The Router's screen: its state, the calls it carries, a row per bus of cable joining it, and what's on
 * those buses, with what each phone is doing.
 */
public final class RouterScreen {

    private RouterScreen() {}

    public static ModularPanel build(MTERouter router, PosGuiData data, PanelSyncManager sync) {
        ViewSync<RouterView> viewSync = new ViewSync<>(
            () -> view(router),
            RouterView::write,
            RouterView::read,
            RouterView.EMPTY);
        sync.syncValue("view", viewSync);
        CanvasWidget screen = new CanvasWidget(new RouterUi(viewSync::view), RouterUi.scale(BeCom.proxy.guiHeight()));
        return new ModularPanel("becom_router").size(screen.width(), screen.height())
            .background(IDrawable.EMPTY)
            .child(screen);
    }

    /** Server side: what the router shows. */
    public static RouterView view(MTERouter router) {
        BeComServer becom = BeComServer.get();
        IGregTechTileEntity tile = router.getBaseMetaTileEntity();
        if (becom == null || tile == null) return RouterView.EMPTY;
        Tunables t = becom.tunables;
        NodeRef self = new NodeRef(
            tile.getWorld().provider.dimensionId,
            BlockPos.pack(tile.getXCoord(), tile.getYCoord(), tile.getZCoord()));
        NetworkModel model = becom.network.model(self.dimension());
        List<RouterView.Bus> buses = new ArrayList<>();
        List<RouterView.Device> devices = new ArrayList<>();
        List<Buses.Bus> on = new ArrayList<>(becom.network.busesAt(self.dimension(), self.pos()));
        on.sort(java.util.Comparator.comparingLong(Buses.Bus::id));
        for (int i = 0; i < on.size(); i++) {
            Buses.Bus bus = on.get(i);
            Object link = NetworkService.busLink(self.dimension(), bus);
            int phones = 0, routers = 0, gateways = 0;
            for (Buses.End end : bus.ends()) {
                if (end.pos() == self.pos()) continue;
                switch (end.kind()) {
                    case PHONE -> phones++;
                    case ROUTER -> routers++;
                    case GATEWAY -> gateways++;
                }
                devices.add(device(becom, model, end, i, bus.tooLong()));
            }
            buses.add(
                new RouterView.Bus(
                    bus.tier()
                        .ordinal(),
                    bus.cables(),
                    t.cableReach(bus.tier()),
                    becom.network.carries(self.dimension(), bus) ? t.cableRate(bus.tier()) : 0,
                    becom.calls.usedOn(link),
                    becom.apps.dataOn(link),
                    phones,
                    routers,
                    gateways));
        }
        // Phones first, by number; then the routers and gateways, as they come.
        devices.sort(
            java.util.Comparator.comparing((RouterView.Device d) -> d.node() >= 0)
                .thenComparingInt(RouterView.Device::number));
        return new RouterView(
            router.tier()
                .ordinal(),
            router.isWorking(),
            becom.calls.callsAt(self.dimension(), self.pos()),
            t.routerCalls(router.tier()),
            buses,
            devices);
    }

    /** One thing on a bus: a phone and what it's doing, or a router or gateway joined there. */
    private static RouterView.Device device(BeComServer becom, NetworkModel model, Buses.End end, int bus,
        boolean tooLong) {
        if (end.kind() != Buses.Kind.PHONE) {
            NetNode node = model == null ? null : model.node(end.pos());
            return new RouterView.Device(
                -1,
                node == null ? -1
                    : node.kind()
                        .ordinal(),
                -1,
                bus,
                RouterView.Now.JOINED,
                "");
        }
        int number = end.number();
        PhoneRecord record = number < 0 ? null : becom.phones.record(number);
        RouterView.Now now = RouterView.Now.IDLE;
        String with = "";
        if (tooLong) {
            now = RouterView.Now.TOO_LONG;
        } else if (number >= 0) {
            Call call = becom.calls.book()
                .callOfPhone(number);
            Call.Leg leg = call == null ? null : call.legOf(number);
            if (leg != null && leg.state() == Call.Leg.State.RINGING) {
                now = RouterView.Now.RINGING;
            } else if (leg != null && leg.state() != Call.Leg.State.LEFT) {
                now = RouterView.Now.CALL;
                with = others(becom, call, number);
            }
        }
        return new RouterView.Device(
            record == null ? -1
                : record.kind()
                    .ordinal(),
            -1,
            number,
            bus,
            now,
            with);
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
