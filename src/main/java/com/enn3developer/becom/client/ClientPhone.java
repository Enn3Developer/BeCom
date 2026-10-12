package com.enn3developer.becom.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.StatCollector;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

import org.lwjgl.input.Keyboard;

import com.enn3developer.becom.item.ItemCarriedPhone;
import com.enn3developer.becom.logic.SignalLevel;
import com.enn3developer.becom.packet.BeComPackets;
import com.enn3developer.becom.packet.PhoneKeyPacket;
import com.enn3developer.becom.packet.RingPacket;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;

/**
 * Carried phones on the client (DESIGN.md 11.1, 11.2): the Phone key, the incoming-call notice in the corner, and the
 * service of the phone in hand for the signal bars. The server decides what the key does.
 */
public final class ClientPhone {

    /** The Phone key's default: N, unbound in a full GTNH install (DESIGN.md 11.1). */
    public static final int DEFAULT_KEY = Keyboard.KEY_N;

    private static final int NOTICE_MARGIN = 4;
    private static final int NOTICE_PADDING = 3;
    private static final int NOTICE_BACKGROUND = 0xC0101010;
    private static final int NOTICE_TEXT = 0xFFE0E0E0;
    private static final int NOTICE_HINT = 0xFFA0A0A0;

    private static KeyBinding key;
    private static volatile RingPacket ringing;
    private static volatile SignalLevel service = SignalLevel.NONE;
    // The weakest level the player hears their call at, or null while they're in none.
    private static volatile SignalLevel call;

    public static void register() {
        key = new KeyBinding("key.becom.phone", DEFAULT_KEY, "key.categories.becom");
        ClientRegistry.registerKeyBinding(key);
    }

    public static void ring(RingPacket packet) {
        ringing = packet.isRinging() ? packet : null;
    }

    public static void service(SignalLevel level) {
        service = level;
    }

    public static void callBars(SignalLevel level) {
        call = level;
    }

    /** The weakest level the player hears their call at, or null while they're in none. */
    public static SignalLevel callLevel() {
        return call;
    }

    /** The service of the phone the player carries first, or null while they hold no phone to show it. */
    public static SignalLevel heldService() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || !ItemCarriedPhone.is(mc.thePlayer.getHeldItem())) return null;
        return service;
    }

    public static void reset() {
        ringing = null;
        service = SignalLevel.NONE;
        call = null;
    }

    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (key == null || mc.thePlayer == null || mc.currentScreen != null || !key.isPressed()) return;
        BeComPackets.CHANNEL.sendToServer(new PhoneKeyPacket(mc.thePlayer.isSneaking()));
    }

    /** The notice of an incoming call (DESIGN.md 11.2): who, from which number, and the keys. */
    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post event) {
        RingPacket ring = ringing;
        if (event.type != RenderGameOverlayEvent.ElementType.ALL || ring == null) return;
        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer font = mc.fontRenderer;
        String keyName = GameSettings.getKeyDisplayString(key.getKeyCode());
        String title = StatCollector.translateToLocalFormatted("becom.phone.notice", ring.caller(), ring.number());
        String hint = StatCollector.translateToLocalFormatted("becom.phone.notice_keys", keyName);
        int width = Math.max(font.getStringWidth(title), font.getStringWidth(hint)) + 2 * NOTICE_PADDING;
        int right = event.resolution.getScaledWidth() - NOTICE_MARGIN;
        int left = right - width;
        int top = NOTICE_MARGIN;
        int bottom = top + 2 * font.FONT_HEIGHT + 3 * NOTICE_PADDING;
        Gui.drawRect(left, top, right, bottom, NOTICE_BACKGROUND);
        font.drawStringWithShadow(title, left + NOTICE_PADDING, top + NOTICE_PADDING, NOTICE_TEXT);
        font.drawStringWithShadow(
            hint,
            left + NOTICE_PADDING,
            top + 2 * NOTICE_PADDING + font.FONT_HEIGHT,
            NOTICE_HINT);
    }
}
