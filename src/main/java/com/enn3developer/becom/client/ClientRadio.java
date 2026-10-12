package com.enn3developer.becom.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.client.event.MouseEvent;

import org.lwjgl.input.Keyboard;

import com.enn3developer.becom.config.BeComConfig;
import com.enn3developer.becom.item.Carried;
import com.enn3developer.becom.item.ItemWalkieTalkie;
import com.enn3developer.becom.machine.MTETransceiver;
import com.enn3developer.becom.packet.BeComPackets;
import com.enn3developer.becom.packet.WalkieChannelPacket;
import com.enn3developer.gtnhvoice.api.client.IAddonActivation;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * Push-to-talk on the client (DESIGN.md 11.1, 13.4): every tick, BeCom's {@code radio} activation is keyed while the
 * key is held and the player has a radio to talk on, a working walkie or a working Transceiver within reach. The
 * activation's transmitting state lights the walkie's TX light. Sneak + scroll with a walkie in hand asks the server
 * for the next channel (DESIGN.md 8.1).
 */
public final class ClientRadio {

    /** The radio push-to-talk key's default: Caps Lock, unbound in a full GTNH install (DESIGN.md 11.1, 16.2). */
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

    /** Whether the player is transmitting on {@code stack} right now, for its TX light (DESIGN.md 11.3). */
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

    @SubscribeEvent
    public void onMouse(MouseEvent event) {
        if (event.dwheel == 0) return;
        EntityClientPlayerMP player = Minecraft.getMinecraft().thePlayer;
        if (player == null || !player.isSneaking() || !ItemWalkieTalkie.is(player.getHeldItem())) return;
        BeComPackets.CHANNEL.sendToServer(new WalkieChannelPacket(event.dwheel > 0 ? 1 : -1));
        // The wheel turns the channel, not the hotbar.
        event.setCanceled(true);
    }
}
