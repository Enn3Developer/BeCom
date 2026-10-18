package com.enn3developer.becom.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.common.UsernameCache;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.enn3developer.becom.BeCom;
import com.enn3developer.becom.gui.machine.AntennaUi;
import com.enn3developer.becom.gui.paint.CanvasWidget;
import com.enn3developer.becom.logic.Positions;
import com.enn3developer.becom.logic.SignalLevel;
import com.enn3developer.becom.logic.Tunables;
import com.enn3developer.becom.logic.net.BlockPos;
import com.enn3developer.becom.logic.net.CableTier;
import com.enn3developer.becom.logic.net.NetNode;
import com.enn3developer.becom.logic.net.NetworkModel;
import com.enn3developer.becom.logic.net.NodeKind;
import com.enn3developer.becom.logic.net.NodeRef;
import com.enn3developer.becom.logic.radio.AntennaNetwork;
import com.enn3developer.becom.machine.MTEAntenna;
import com.enn3developer.becom.server.BeComServer;
import com.enn3developer.becom.server.NetworkService;
import com.enn3developer.becom.server.Ownership;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * The antenna screen's server side: the view it shows ({@link AntennaUi} paints it) and the team lock its owner can
 * set.
 */
public final class AntennaScreen {

    private static final String LOCK = "lock";
    // How close a player must stand to lock an antenna, as for any machine's screen.
    private static final double LOCK_REACH = 8;

    private AntennaScreen() {}

    public static ModularPanel build(MTEAntenna antenna, PosGuiData data, PanelSyncManager sync) {
        EntityPlayer player = data.getPlayer();
        ViewSync<AntennaView> viewSync = new ViewSync<>(
            () -> view(antenna, player),
            AntennaView::write,
            AntennaView::read,
            AntennaView.EMPTY);
        sync.syncValue("view", viewSync);
        sync.registerSyncedAction(LOCK, false, true, buf -> setLocked(antenna, player, buf.readBoolean()));
        CanvasWidget screen = new CanvasWidget(
            new AntennaUi(viewSync::view, on -> sync.callSyncedAction(LOCK, buf -> buf.writeBoolean(on))),
            AntennaUi.scale(BeCom.proxy.guiHeight()));
        return new ModularPanel("becom_antenna").size(screen.width(), screen.height())
            .background(IDrawable.EMPTY)
            .child(screen);
    }

    /** Server side: only the owner, their team or an operator may lock. */
    public static void setLocked(MTEAntenna antenna, EntityPlayer player, boolean locked) {
        IGregTechTileEntity tile = antenna.getBaseMetaTileEntity();
        if (tile == null || tile.isDead() || !Ownership.mayManage(player, antenna.owner())) return;
        double distanceSq = player
            .getDistanceSq(tile.getXCoord() + 0.5, tile.getYCoord() + 0.5, tile.getZCoord() + 0.5);
        if (!Positions.withinSq(distanceSq, LOCK_REACH)) return;
        antenna.setLocked(locked);
    }

    /** Server side: the view for one player. */
    public static AntennaView view(MTEAntenna antenna, EntityPlayer player) {
        BeComServer becom = BeComServer.get();
        IGregTechTileEntity tile = antenna.getBaseMetaTileEntity();
        if (becom == null || tile == null) return AntennaView.EMPTY;
        Tunables t = becom.tunables;
        NodeRef ref = antenna.ref();
        AntennaNetwork.Antenna known = becom.antennas.antenna(ref);
        List<AntennaView.Link> links = new ArrayList<>();
        for (Map.Entry<AntennaNetwork.LinkKey, AntennaNetwork.Link> entry : becom.antennas.links(ref.dimension())
            .entrySet()) {
            AntennaNetwork.LinkKey key = entry.getKey();
            if (key.a() != ref.pos() && key.b() != ref.pos()) continue;
            long other = key.a() == ref.pos() ? key.b() : key.a();
            AntennaNetwork.Link link = entry.getValue();
            links.add(
                new AntennaView.Link(
                    BlockPos.x(other),
                    BlockPos.y(other),
                    BlockPos.z(other),
                    (int) Math.round(BlockPos.distance(ref.pos(), other)),
                    link.vhf()
                        .snr(),
                    link.uhf()
                        .snr()));
        }
        links.sort(Comparator.comparingDouble((AntennaView.Link l) -> -Math.max(l.vhf(), l.uhf())));
        List<AntennaView.Bridge> bridges = new ArrayList<>();
        NetNode node = becom.network.model(tile.getWorld())
            .node(ref.pos());
        if (node != null) {
            NetworkModel model = becom.network.model(tile.getWorld());
            List<NetworkService.Run> runs = becom.network.runsFrom(ref.dimension(), ref.pos());
            for (long other : node.links()) {
                List<CableTier> cables = model.cables(ref.pos(), other);
                // The span leads to a pylon or a machine; the run it starts goes on to the next working node.
                NetworkService.Run run = runOver(
                    runs,
                    other,
                    node.links()
                        .size());
                long end = run == null ? other : run.to();
                NetNode far = model.node(end);
                bridges.add(
                    new AntennaView.Bridge(
                        far == null || far.kind() == NodeKind.PYLON ? -1
                            : far.kind()
                                .ordinal(),
                        BlockPos.x(end),
                        BlockPos.y(end),
                        BlockPos.z(end),
                        (int) Math.round(run == null ? BlockPos.distance(ref.pos(), other) : run.length()),
                        cables.isEmpty() ? -1
                            : cables.get(0)
                                .ordinal(),
                        run == null ? -1 : run.rate(),
                        run == null ? 0 : becom.calls.usedOn(run.link()) + becom.apps.dataOn(run.link())));
            }
        }
        double bestUhf = Double.NEGATIVE_INFINITY;
        for (AntennaView.Link link : links) bestUhf = Math.max(bestUhf, link.uhf());
        SignalLevel radioLevel = links.isEmpty() ? SignalLevel.NONE
            : t.levelScale()
                .levelOf(bestUhf);
        int radioRate = links.isEmpty() ? 0 : t.radioRate(radioLevel);
        UUID owner = antenna.owner();
        String ownerName = owner == null ? "-" : UsernameCache.getLastKnownUsername(owner);
        byte[] vhfMap = known == null ? null : becom.antennas.coverage(known, becom.antennas.vhf());
        byte[] uhfMap = known == null ? null : becom.antennas.coverage(known, becom.antennas.uhf());
        return new AntennaView(
            antenna.tier(),
            antenna.mMachine,
            antenna.isWorking(),
            antenna.mast(),
            (int) Math.round(t.rangeToLow(t.antennaBudget(antenna.tier()))),
            known != null && known.survey() != null,
            antenna.isLocked(),
            Ownership.mayManage(player, owner),
            ownerName == null ? "-" : ownerName,
            becom.calls.callsAt(ref.dimension(), ref.pos()),
            t.antennaCalls(antenna.tier()),
            links,
            bridges,
            (int) Math.round(t.rangeToHold(t.antennaBudget(antenna.tier()))),
            vhfMap == null ? new byte[0] : vhfMap,
            uhfMap == null ? new byte[0] : uhfMap,
            radioRate,
            radioLevel.ordinal());
    }

    /** The run leaving over the span to {@code other}: the one ending there, or the only one when there's one span. */
    private static NetworkService.Run runOver(List<NetworkService.Run> runs, long other, int spans) {
        for (NetworkService.Run run : runs) if (run.to() == other) return run;
        return spans == 1 && runs.size() == 1 ? runs.get(0) : null;
    }
}
