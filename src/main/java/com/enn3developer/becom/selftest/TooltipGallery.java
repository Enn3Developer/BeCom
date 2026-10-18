package com.enn3developer.becom.selftest;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;

import com.enn3developer.becom.block.BeComBlocks;
import com.enn3developer.becom.item.BeComItems;
import com.enn3developer.becom.item.ItemCarriedPhone;
import com.enn3developer.becom.item.ItemWalkieTalkie;
import com.enn3developer.becom.logic.SignalLevel;
import com.enn3developer.becom.logic.net.CableTier;
import com.enn3developer.becom.machine.BeComMachines;
import com.enn3developer.becom.machine.InfoLines;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import ic2.api.item.ElectricItem;
import mcp.mobius.waila.utils.ModIdentification;

/**
 * For the client self-test: the Tooltips board's items, or the Scanner board's WAILA boxes, as this client draws them,
 * side by side for a screenshot.
 */
@SideOnly(Side.CLIENT)
final class TooltipGallery extends GuiScreen {

    private final List<List<String>> boxes = new ArrayList<>();
    private final int[][] at;

    TooltipGallery(boolean scanner) {
        if (scanner) {
            // The Scanner board's boxes, as their machines word them today.
            boxes.add(waila());
            boxes.add(router(false));
            boxes.add(telephone());
            boxes.add(router(true));
            boxes.add(pylon());
            boxes.add(relay());
            at = new int[][] { { 12, 24 }, { 220, 24 }, { 430, 24 }, { 12, 150 }, { 220, 150 }, { 400, 150 } };
            return;
        }
        // The walkies, then the phones, as on the Tooltips board; the antenna's long GT lines under them, and the
        // WAILA box on the right.
        at = new int[][] { { 12, 24 }, { 12, 110 }, { 170, 24 }, { 170, 110 }, { 12, 220 }, { 440, 24 } };
        boxes.add(item(walkie(true, 0.61)));
        boxes.add(item(walkie(false, 0)));
        ItemStack mobile = new ItemStack(BeComItems.mobilePhone);
        ItemCarriedPhone.setOn(mobile, true);
        ItemCarriedPhone.setNumber(mobile, 482913);
        ElectricItem.manager
            .charge(mobile, BeComItems.mobilePhone.maxCharge(mobile) / 2, Integer.MAX_VALUE, true, false);
        boxes.add(item(mobile));
        ItemStack me = new ItemStack(BeComItems.mePhone);
        ItemCarriedPhone.setOn(me, true);
        ItemCarriedPhone.setNumber(me, 119200);
        // It runs on AE power, which IC2's manager doesn't charge.
        BeComItems.mePhone.injectAEPower(me, BeComItems.mePhone.getAEMaxPower(me) * 0.9);
        boxes.add(item(me));
        boxes.add(item(BeComMachines.antennaT2.getStackForm(1)));
        boxes.add(waila());
    }

    /** An item's tooltip as NEI shows it in an inventory, which this screen isn't: its lines, then its mod. */
    private static List<String> item(ItemStack stack) {
        List<String> box = new ArrayList<>();
        for (Object line : stack.getTooltip(Minecraft.getMinecraft().thePlayer, false)) box.add(String.valueOf(line));
        box.add(mod(stack));
        return box;
    }

    /** The line WAILA ends a tooltip or a box with, in NEI too: the mod the item is registered by. */
    private static String mod(ItemStack stack) {
        return "§9§o" + ModIdentification.nameFromStack(stack);
    }

    private static ItemStack walkie(boolean on, double charge) {
        ItemStack stack = new ItemStack(BeComItems.walkieTalkie);
        ItemWalkieTalkie.setChannel(stack, 4);
        ItemWalkieTalkie.setOn(stack, on);
        if (charge > 0) ElectricItem.manager
            .charge(stack, BeComItems.walkieTalkie.getMaxCharge(stack) * charge, Integer.MAX_VALUE, true, false);
        return stack;
    }

    /** A Router's WAILA lines as on the Scanner board: working, or with every call taken and a bus too long. */
    private static List<String> router(boolean full) {
        InfoLines info = new InfoLines();
        info.add("becom.info.online")
            .status();
        if (full) {
            info.add("becom.info.of_slots", 8, 8)
                .bar("becom.info.row.calls", 1);
            info.add("becom.info.bus", InfoLines.cableName(CableTier.PHONE), 40, 128, 16, 16)
                .row("becom.info.row.bus");
            info.problem("becom.info.bus_too_long", InfoLines.cableName(CableTier.PHONE), 128)
                .row("becom.info.row.bus");
            info.bundle(List.of(CableTier.COAXIAL), "becom.info.uplink", 30, 64)
                .row("becom.info.row.uplink");
        } else {
            info.add("becom.info.of_slots", 3, 8)
                .bar("becom.info.row.calls", 3 / 8.0);
            info.add("becom.info.bus", InfoLines.cableName(CableTier.PHONE), 14, 128, 12, 16)
                .row("becom.info.row.bus");
            info.bundle(List.of(CableTier.PHONE, CableTier.PHONE), "becom.info.uplink", 12, 32)
                .row("becom.info.row.uplink");
        }
        return box(BeComMachines.router.getStackForm(1), info);
    }

    /** A ringing Telephone's WAILA lines as on the Scanner board. */
    private static List<String> telephone() {
        InfoLines info = new InfoLines();
        info.add("becom.info.ringing")
            .statusBusy();
        info.add("becom.info.value", 482913)
            .row("becom.info.row.number");
        info.add("becom.info.value", "Steve")
            .row("becom.info.row.owner");
        info.add("becom.info.bus", InfoLines.cableName(CableTier.PHONE), 14, 128, 12, 16)
            .row("becom.info.row.bus");
        return box(new ItemStack(BeComBlocks.telephone), info);
    }

    /** A Pylon Head's WAILA lines as on the Scanner board: no state line while it's formed. */
    private static List<String> pylon() {
        InfoLines info = new InfoLines();
        info.add("becom.info.blocks", 12)
            .row("becom.info.row.pole");
        info.bundle(List.of(CableTier.FIBRE, CableTier.FIBRE), "becom.info.span_row", 45)
            .row("becom.info.row.dir.east");
        info.bundle(List.of(CableTier.FIBRE, CableTier.FIBRE), "becom.info.span_row", 51)
            .row("becom.info.row.dir.west");
        info.add("becom.info.of", 600, 4096)
            .row("becom.info.row.run");
        return box(BeComMachines.pylonHead.getStackForm(1), info);
    }

    /**
     * A paired Portal Relay's WAILA lines. The Scanner board's relay box predates the Gateways board, which words it
     * as this does.
     */
    private static List<String> relay() {
        InfoLines info = new InfoLines();
        info.add("becom.relay.status_paired")
            .status();
        info.add("becom.info.eu_t", 32)
            .right();
        info.add("becom.relay.portal_kind", new InfoLines.Key("becom.portal_name.nether"), 3)
            .row("becom.info.row.portal");
        info.add("becom.relay.far_side", "MV", "12, 64, 25", "Nether")
            .row("becom.info.row.far_side");
        info.add("becom.relay.hop_fixed", new InfoLines.Key("becom.level.high"))
            .level("becom.info.row.hop", SignalLevel.HIGH);
        info.add("becom.info.of_slots", 5, 16)
            .bar("becom.info.row.calls", 5 / 16.0);
        info.add("becom.info.backhaul_value", new InfoLines.Key("becom.ui.cable.fibre"), 10, 256)
            .row("becom.info.row.backhaul");
        info.add("becom.info.backhaul_value", new InfoLines.Key("becom.ui.cable.coaxial"), 10, 64)
            .row("becom.info.row.far_backhaul");
        return box(BeComMachines.portalRelayHV.getStackForm(1), info);
    }

    /** A WAILA box: the block's name on top and its mod at the bottom, as WAILA writes them, its lines between. */
    private static List<String> box(ItemStack block, InfoLines info) {
        List<String> lines = new ArrayList<>();
        lines.add(block.getDisplayName());
        lines.addAll(info.translate());
        lines.add(mod(block));
        return lines;
    }

    /** An Antenna T2's WAILA lines as on the Scanner board. */
    private static List<String> waila() {
        InfoLines info = new InfoLines();
        info.add("becom.info.online")
            .status();
        info.add("becom.info.blocks", 24)
            .row("becom.info.row.mast");
        info.add("becom.info.links_backhaul", 2, InfoLines.shortName(CableTier.FIBRE))
            .row("becom.info.row.links");
        info.add("becom.info.of", 9, 12)
            .row("becom.info.row.calls");
        info.add("becom.info.serves_team", "Steve")
            .row("becom.info.row.serves");
        return box(BeComMachines.antennaT2.getStackForm(1), info);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        for (int i = 0; i < boxes.size(); i++) {
            drawHoveringText(boxes.get(i), at[i][0], at[i][1], fontRendererObj);
        }
    }
}
