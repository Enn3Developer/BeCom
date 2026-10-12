package com.enn3developer.becom.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.UsernameCache;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.BoolValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ToggleButton;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.enn3developer.becom.config.BeComConfig;
import com.enn3developer.becom.logic.LevelScale;
import com.enn3developer.becom.logic.SignalLevel;
import com.enn3developer.becom.logic.Tunables;
import com.enn3developer.becom.logic.net.BlockPos;
import com.enn3developer.becom.logic.net.NetNode;
import com.enn3developer.becom.logic.net.NodeRef;
import com.enn3developer.becom.logic.radio.AntennaNetwork;
import com.enn3developer.becom.machine.MTEAntenna;
import com.enn3developer.becom.server.BeComServer;
import com.enn3developer.becom.server.Ownership;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * The antenna screen (DESIGN.md 11.4, 12.2): its state, its links to other antennas on each band, its bridges onto
 * wired lines, and the team lock its owner can set. The coverage map joins it in 0.3, as DESIGN.md 16 plans.
 */
public final class AntennaScreen {

    private static final int WIDTH = 220;
    private static final int HEIGHT = 170;
    private static final int ROWS = 7;
    private static final int ROW_HEIGHT = 11;
    private static final String LOCK = "lock";

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
        Supplier<AntennaView> view = viewSync::view;

        ModularPanel panel = new ModularPanel("becom_antenna").size(WIDTH, HEIGHT);
        Flow column = Flow.column()
            .sizeRel(1f)
            .padding(6, 6, 6, 6)
            .childPadding(2);
        column.child(
            IKey.dynamic(
                () -> StatCollector.translateToLocalFormatted(
                    "becom.antenna.title",
                    view.get()
                        .tier()))
                .asWidget()
                .widthRel(1f)
                .height(10));
        column.child(
            IKey.dynamic(() -> status(view.get()))
                .asWidget()
                .widthRel(1f)
                .height(10));
        column.child(
            IKey.dynamic(
                () -> StatCollector.translateToLocalFormatted(
                    "becom.antenna.owner",
                    view.get()
                        .owner()))
                .asWidget()
                .widthRel(1f)
                .height(10));
        column.child(
            new ToggleButton().size(WIDTH - 12, 14)
                .overlay(IKey.lang("becom.antenna.lock"))
                .value(
                    new BoolValue.Dynamic(
                        () -> view.get()
                            .locked(),
                        on -> sync.callSyncedAction(LOCK, buf -> buf.writeBoolean(on))))
                .setEnabledIf(
                    w -> view.get()
                        .mayManage()));
        IntSupplier size = () -> view.get()
            .links()
            .size()
            + view.get()
                .bridges()
                .size();
        int[] page = { 0 };
        for (int r = 0; r < ROWS; r++) {
            int row = r;
            column.child(
                IKey.dynamic(() -> line(view.get(), page[0] * ROWS + row))
                    .asWidget()
                    .widthRel(1f)
                    .height(ROW_HEIGHT));
        }
        column.child(pager(page, size));
        panel.child(column);
        return panel;
    }

    private static String status(AntennaView view) {
        if (!view.formed()) return StatCollector.translateToLocal("becom.antenna.unformed");
        String state = StatCollector.translateToLocal(view.working() ? "becom.info.online" : "becom.info.offline");
        String survey = StatCollector
            .translateToLocal(view.surveyed() ? "becom.antenna.surveyed" : "becom.antenna.surveying");
        return StatCollector
            .translateToLocalFormatted("becom.antenna.status", state, view.mast(), view.range(), survey);
    }

    private static String line(AntennaView view, int i) {
        List<AntennaView.Link> links = view.links();
        if (i < links.size()) {
            AntennaView.Link link = links.get(i);
            return StatCollector.translateToLocalFormatted(
                "becom.antenna.link",
                link.x(),
                link.y(),
                link.z(),
                link.distance(),
                band(link.vhf()),
                band(link.uhf()));
        }
        i -= links.size();
        List<AntennaView.Bridge> bridges = view.bridges();
        if (i < bridges.size()) {
            AntennaView.Bridge bridge = bridges.get(i);
            return StatCollector.translateToLocalFormatted(
                "becom.antenna.bridge",
                bridge.x(),
                bridge.y(),
                bridge.z(),
                bridge.distance());
        }
        return i == 0 && links.isEmpty() ? StatCollector.translateToLocal("becom.antenna.no_links") : "";
    }

    /** A band's level and SNR, or a dash below Low. */
    private static String band(double snr) {
        LevelScale scale = BeComConfig.get()
            .levelScale();
        SignalLevel level = SignalLevel.min(scale.levelOf(snr), SignalLevel.MEDIUM);
        if (!level.atLeast(SignalLevel.LOW)) return "-";
        return StatCollector.translateToLocal(
            "becom.level." + level.name()
                .toLowerCase(Locale.ROOT))
            + " "
            + Math.round(snr)
            + " dB";
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

    /** Server side: only the owner, their team or an operator may lock (DESIGN.md 12.2, D38). */
    public static void setLocked(MTEAntenna antenna, EntityPlayer player, boolean locked) {
        IGregTechTileEntity tile = antenna.getBaseMetaTileEntity();
        if (tile == null || tile.isDead() || !Ownership.mayManage(player, antenna.owner())) return;
        if (player.getDistanceSq(tile.getXCoord() + 0.5, tile.getYCoord() + 0.5, tile.getZCoord() + 0.5) > 64) return;
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
            for (long other : node.links()) {
                bridges.add(
                    new AntennaView.Bridge(
                        BlockPos.x(other),
                        BlockPos.y(other),
                        BlockPos.z(other),
                        (int) Math.round(BlockPos.distance(ref.pos(), other))));
            }
        }
        UUID owner = antenna.owner();
        String ownerName = owner == null ? "-" : UsernameCache.getLastKnownUsername(owner);
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
            links,
            bridges);
    }
}
