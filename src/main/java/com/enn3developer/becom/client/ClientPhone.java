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
 * Carried phones on the client: the Phone key, the incoming-call notice in the corner, and the
 * service of the phone in hand for the signal bars. The server decides what the key does.
 */
public final class ClientPhone {

    /** The Phone key's default: N, unbound in a full GTNH install. */
    public static final int DEFAULT_KEY = Keyboard.KEY_N;

    // The HUD board's notice, in GUI pixels: half the board's, which is drawn at GUI scale 2.
    private static final int NOTICE_MARGIN = 4;
    private static final int NOTICE_PAD_X = 5;
    private static final int NOTICE_PAD_Y = 4;
    private static final int NOTICE_GAP = 3;
    private static final int NOTICE_BACKGROUND = 0xD9101010;
    private static final int NOTICE_BORDER = 0xFF55FF55;
    private static final int NOTICE_TITLE = 0xFFFFFFFF;
    private static final int NOTICE_NUMBER = 0xFF8A8A8A;
    private static final int NOTICE_TEXT = 0xFFC0C0C0;
    private static final int NOTICE_ANSWER = 0xFF55FF55;
    private static final int NOTICE_DECLINE = 0xFFFF6B55;
    private static final int CAP_HEIGHT = 9;

    private static KeyBinding key;
    private static volatile RingPacket ringing;
    private static volatile SignalLevel service = SignalLevel.NONE;
    // The weakest level the player hears their call at, or null while they're in none.
    private static volatile SignalLevel call;

    public static void register() {
        key = new KeyBinding("key.becom.phone", DEFAULT_KEY, "key.categories.becom");
        ClientRegistry.registerKeyBinding(key);
    }

    /** The Phone key's name as the player bound it. */
    public static String keyName() {
        return key == null ? "N" : GameSettings.getKeyDisplayString(key.getKeyCode());
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

    /**
     * The notice of an incoming call, as on the HUD board: who and from which number, then the keys as key caps, N to
     * answer and Shift + N to decline. It stands under the video window, in its corner, or in the top right.
     */
    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post event) {
        RingPacket ring = ringing;
        if (event.type != RenderGameOverlayEvent.ElementType.ALL || ring == null) return;
        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer font = mc.fontRenderer;
        int screenWidth = event.resolution.getScaledWidth();
        int screenHeight = event.resolution.getScaledHeight();
        String title = StatCollector.translateToLocalFormatted("becom.hud.calling", ring.caller());
        String number = String.valueOf(ring.number());
        String keyName = GameSettings.getKeyDisplayString(key.getKeyCode());
        String answer = StatCollector.translateToLocal("becom.hud.answer");
        String decline = StatCollector.translateToLocal("becom.hud.decline");
        int sneakCode = mc.gameSettings.keyBindSneak.getKeyCode();
        String sneak = sneakCode == Keyboard.KEY_LSHIFT || sneakCode == Keyboard.KEY_RSHIFT
            ? StatCollector.translateToLocal("becom.hud.shift")
            : GameSettings.getKeyDisplayString(sneakCode);
        int titleWidth = font.getStringWidth(title) + 4 + font.getStringWidth(number);
        int keysWidth = cap(font, keyName) + NOTICE_GAP
            + font.getStringWidth(answer)
            + 3 * NOTICE_GAP
            + cap(font, sneak)
            + NOTICE_GAP
            + font.getStringWidth("+")
            + NOTICE_GAP
            + cap(font, keyName)
            + NOTICE_GAP
            + font.getStringWidth(decline);
        int width = Math.max(titleWidth, keysWidth) + 2 * NOTICE_PAD_X + 2;
        int height = 2 + 2 * NOTICE_PAD_Y + font.FONT_HEIGHT + NOTICE_GAP + CAP_HEIGHT;
        int[] window = VideoRenderer.window(screenWidth, screenHeight);
        boolean right = window == null || window[0] + window[2] / 2 > screenWidth / 2;
        boolean bottom = window != null && window[1] + window[3] / 2 > screenHeight / 2;
        int left = right ? (window == null ? screenWidth - NOTICE_MARGIN : window[0] + window[2]) - width : window[0];
        int top = window == null ? NOTICE_MARGIN
            : bottom ? window[1] - NOTICE_MARGIN - height : window[1] + window[3] + NOTICE_MARGIN;
        // The border as four edges: under the see-through background, a filled one would tint it green.
        Gui.drawRect(left, top, left + width, top + 1, NOTICE_BORDER);
        Gui.drawRect(left, top + height - 1, left + width, top + height, NOTICE_BORDER);
        Gui.drawRect(left, top + 1, left + 1, top + height - 1, NOTICE_BORDER);
        Gui.drawRect(left + width - 1, top + 1, left + width, top + height - 1, NOTICE_BORDER);
        Gui.drawRect(left + 1, top + 1, left + width - 1, top + height - 1, NOTICE_BACKGROUND);
        int x = left + 1 + NOTICE_PAD_X;
        int y = top + 1 + NOTICE_PAD_Y;
        font.drawStringWithShadow(title, x, y, NOTICE_TITLE);
        font.drawString(number, x + font.getStringWidth(title) + 4, y, NOTICE_NUMBER);
        // The keys' line: its text sits in the middle of the key caps' height.
        int capTop = y + font.FONT_HEIGHT + NOTICE_GAP;
        y = capTop + 1;
        x += drawCap(font, keyName, x, capTop) + NOTICE_GAP;
        font.drawStringWithShadow(answer, x, y, NOTICE_ANSWER);
        x += font.getStringWidth(answer) + 3 * NOTICE_GAP;
        x += drawCap(font, sneak, x, capTop) + NOTICE_GAP;
        font.drawStringWithShadow("+", x, y, NOTICE_TEXT);
        x += font.getStringWidth("+") + NOTICE_GAP;
        x += drawCap(font, keyName, x, capTop) + NOTICE_GAP;
        font.drawStringWithShadow(decline, x, y, NOTICE_DECLINE);
    }

    private static int cap(FontRenderer font, String key) {
        return Math.max(CAP_HEIGHT, font.getStringWidth(key) + 4);
    }

    /** A key cap as on the HUD board: the key's name on a dark key, lit from the top left; returns its width. */
    private static int drawCap(FontRenderer font, String key, int x, int top) {
        int w = cap(font, key);
        Gui.drawRect(x, top, x + w, top + CAP_HEIGHT, 0xFF1A1A1A);
        Gui.drawRect(x, top, x + w - 1, top + CAP_HEIGHT - 1, 0xFF8A8A8A);
        Gui.drawRect(x + 1, top + 1, x + w - 1, top + CAP_HEIGHT - 1, 0xFF3A3A3A);
        font.drawString(key, x + (w - font.getStringWidth(key) + 1) / 2, top + 1, 0xFFFFFFFF);
        return w;
    }
}
