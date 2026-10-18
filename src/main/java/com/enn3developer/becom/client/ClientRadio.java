package com.enn3developer.becom.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.StatCollector;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import com.enn3developer.becom.config.BeComConfig;
import com.enn3developer.becom.item.Carried;
import com.enn3developer.becom.item.ItemWalkieTalkie;
import com.enn3developer.becom.machine.MTETransceiver;
import com.enn3developer.becom.packet.BeComPackets;
import com.enn3developer.becom.packet.WalkieChannelPacket;
import com.enn3developer.becom.voice.RadioGroup;
import com.enn3developer.gtnhvoice.api.client.IAddonActivation;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * Push-to-talk on the client: every tick, BeCom's {@code radio} activation is keyed while the
 * key is held and the player has a radio to talk on, a working walkie or a working Transceiver within reach. The
 * activation's transmitting state lights the walkie's TX light. Sneak + scroll with a walkie in hand asks the server
 * for the next channel.
 */
public final class ClientRadio {

    /** The radio push-to-talk key's default: Caps Lock, unbound in a full GTNH install. */
    public static final int DEFAULT_KEY = Keyboard.KEY_CAPITAL;

    private static KeyBinding key;
    private static IAddonActivation activation;
    private static ItemStack talkingOn;

    public static void register() {
        key = new KeyBinding("key.becom.radio", DEFAULT_KEY, "key.categories.becom");
        ClientRegistry.registerKeyBinding(key);
        if (ClientAudio.addon() != null) activation = ClientAudio.addon()
            .activation("radio");
    }

    /** Re-asserted every tick, as GTNH Voice asks, so every path that ends the gesture unkeys it (13.4). */
    public static void tick() {
        if (activation == null) return;
        Minecraft mc = Minecraft.getMinecraft();
        EntityClientPlayerMP player = mc.thePlayer;
        boolean held = player != null && !player.isDead && mc.currentScreen == null && key.getIsKeyPressed();
        boolean atTransceiver = player != null && transceiverNear(player);
        Carried.Slot slot = player == null ? null : ItemWalkieTalkie.active(player);
        talkingOn = held && !atTransceiver && slot != null ? slot.stack() : null;
        activation.setKeyed(held && (atTransceiver || slot != null));
    }

    /** Whether the player is transmitting on {@code stack} right now, for its TX light. */
    public static boolean transmittingOn(ItemStack stack) {
        return activation != null && stack != null && stack == talkingOn && activation.isTransmitting();
    }

    /** A working Transceiver within talk range, measured as the server measures it. */
    private static boolean transceiverNear(EntityClientPlayerMP player) {
        double range = BeComConfig.get()
            .talkRange();
        double feet = player.boundingBox.minY;
        int reach = (int) Math.ceil(range) + 1;
        int px = (int) Math.floor(player.posX);
        int py = (int) Math.floor(feet);
        int pz = (int) Math.floor(player.posZ);
        for (int x = px - reach; x <= px + reach; x++) {
            for (int y = py - reach; y <= py + reach + 1; y++) {
                for (int z = pz - reach; z <= pz + reach; z++) {
                    TileEntity tile = player.worldObj.getTileEntity(x, y, z);
                    if (!(tile instanceof IGregTechTileEntity gt) || !(gt.getMetaTileEntity() instanceof MTETransceiver)
                        || !gt.isActive()) continue;
                    double dx = x + 0.5 - player.posX;
                    double dy = y + 0.5 - (feet + 1);
                    double dz = z + 0.5 - player.posZ;
                    if (dx * dx + dy * dy + dz * dz <= range * range) return true;
                }
            }
        }
        return false;
    }

    /** Called when the client leaves a world: nothing stays keyed (13.4). */
    public static void reset() {
        talkingOn = null;
        if (activation != null) activation.setKeyed(false);
    }

    // When the channel last turned by sneak + scroll, in ms: the strip shows for a while after.
    private static long turned = Long.MIN_VALUE / 2;
    private static final long STRIP_MS = 2000;

    /**
     * The walkie on the HUD, as on the HUD board: over the hotbar, a red TX lamp with its channel while the player
     * transmits, and a strip of the channels either side for a moment after they turn it.
     */
    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.HOTBAR) return;
        Minecraft mc = Minecraft.getMinecraft();
        EntityClientPlayerMP player = mc.thePlayer;
        if (player == null) return;
        ItemStack walkie = ItemWalkieTalkie.is(player.getHeldItem()) ? player.getHeldItem() : talkingOn;
        if (walkie == null) return;
        FontRenderer font = mc.fontRenderer;
        int centre = event.resolution.getScaledWidth() / 2;
        // Over the armour and health rows.
        int bottom = event.resolution.getScaledHeight() - 51;
        int channel = ItemWalkieTalkie.channel(walkie);
        boolean tx = transmittingOn(walkie);
        if (tx) {
            String label = StatCollector.translateToLocalFormatted("becom.hud.channel", channel);
            int w = 6 + 6 + font.getStringWidth("TX") + 5 + font.getStringWidth(label) + 6;
            int left = centre - w / 2;
            int top = bottom - font.FONT_HEIGHT - 5;
            Gui.drawRect(left, top, left + w, bottom, 0xC0202820);
            Gui.drawRect(left + 6, top + 4, left + 11, top + 9, 0xFFFF4444);
            font.drawStringWithShadow("TX", left + 13, top + 3, 0xFFFF5555);
            font.drawStringWithShadow(label, left + 13 + font.getStringWidth("TX") + 5, top + 3, 0xFFE0E0E0);
            bottom = top - 3;
        }
        if (System.currentTimeMillis() - turned < STRIP_MS && ItemWalkieTalkie.is(player.getHeldItem())) {
            int before = Math.floorMod(channel - 2, RadioGroup.CHANNELS) + 1;
            int after = Math.floorMod(channel, RadioGroup.CHANNELS) + 1;
            String now = "\u25c2 " + channel + " \u25b8";
            int y = bottom - font.FONT_HEIGHT;
            int nw = font.getStringWidth(now);
            font.drawStringWithShadow(now, centre - nw / 2, y, 0xFFFFFFFF);
            String b = String.valueOf(before);
            font.drawStringWithShadow(b, centre - nw / 2 - 10 - font.getStringWidth(b), y, 0xFF707070);
            font.drawStringWithShadow(String.valueOf(after), centre + nw / 2 + 10, y, 0xFF707070);
        }
        GL11.glColor4f(1, 1, 1, 1);
    }

    @SubscribeEvent
    public void onMouse(MouseEvent event) {
        if (event.dwheel == 0) return;
        EntityClientPlayerMP player = Minecraft.getMinecraft().thePlayer;
        if (player == null || !player.isSneaking() || !ItemWalkieTalkie.is(player.getHeldItem())) return;
        BeComPackets.CHANNEL.sendToServer(new WalkieChannelPacket(event.dwheel > 0 ? 1 : -1));
        turned = System.currentTimeMillis();
        // The wheel turns the channel, not the hotbar.
        event.setCanceled(true);
    }
}
