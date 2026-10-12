package com.enn3developer.becom.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.PositionedSound;
import net.minecraft.util.ResourceLocation;

import com.enn3developer.becom.BeCom;
import com.enn3developer.becom.packet.TonePacket;

/**
 * Plays the handset's call tones to this player only (DESIGN.md 10, 15.2): dial tone, ringback and busy loop until
 * the next tone replaces them; the unreachable tones and the hang-up click play once.
 */
public final class ToneSounds {

    private static ISound current;

    private ToneSounds() {}

    public static void play(TonePacket.Tone tone) {
        Minecraft mc = Minecraft.getMinecraft();
        if (current != null) {
            mc.getSoundHandler()
                .stopSound(current);
            current = null;
        }
        String name = switch (tone) {
            case NONE -> null;
            case DIAL -> "tone.dial";
            case RINGBACK -> "tone.ringback";
            case BUSY -> "tone.busy";
            case UNREACHABLE -> "tone.unreachable";
            case HANG_UP -> "phone.hangup";
        };
        if (name == null) return;
        boolean loop = tone == TonePacket.Tone.DIAL || tone == TonePacket.Tone.RINGBACK || tone == TonePacket.Tone.BUSY;
        current = new Tone(new ResourceLocation(BeCom.MODID, name), loop);
        mc.getSoundHandler()
            .playSound(current);
    }

    public static void stop() {
        play(TonePacket.Tone.NONE);
    }

    /** A sound with no position, heard at full volume like a handset at the ear. */
    private static final class Tone extends PositionedSound {

        Tone(ResourceLocation sound, boolean loop) {
            super(sound);
            this.repeat = loop;
            this.field_147665_h = 0;
            this.volume = 1.0f;
            this.field_147663_c = 1.0f;
            this.field_147666_i = ISound.AttenuationType.NONE;
        }
    }
}
