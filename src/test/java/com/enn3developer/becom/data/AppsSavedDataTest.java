package com.enn3developer.becom.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.Test;

import com.enn3developer.becom.logic.app.AppManifest;
import com.enn3developer.becom.logic.app.AppStore;
import com.enn3developer.becom.logic.app.PhoneApps;

/**
 * Phone apps and the App Store survive a save, written as Minecraft writes the file: even an app of the largest size,
 * which an NBT string (written with writeUTF, 65,535 bytes at most) couldn't hold.
 */
class AppsSavedDataTest {

    private static final UUID STEVE = new UUID(1, 1);

    /** An app of exactly {@code bytes} bytes of UTF-8, padded with {@code pad}. */
    private static String app(String name, int bytes, String pad) {
        StringBuilder source = new StringBuilder("--@name " + name + "\nreturn {}\n--");
        int padBytes = pad.getBytes(StandardCharsets.UTF_8).length;
        while (source.toString()
            .getBytes(StandardCharsets.UTF_8).length + padBytes <= bytes) source.append(pad);
        while (source.toString()
            .getBytes(StandardCharsets.UTF_8).length < bytes) source.append('x');
        return source.toString();
    }

    private static AppsSavedData roundTrip(AppsSavedData data) throws IOException {
        NBTTagCompound nbt = new NBTTagCompound();
        data.writeToNBT(nbt);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CompressedStreamTools.writeCompressed(nbt, out);
        AppsSavedData back = new AppsSavedData(AppsSavedData.NAME);
        back.readFromNBT(CompressedStreamTools.readCompressed(new ByteArrayInputStream(out.toByteArray())));
        return back;
    }

    @Test
    void theLargestAppsSurviveASave() throws IOException {
        String ascii = app("Big", AppManifest.MAX_SOURCE, "a");
        // Characters outside the BMP take 6 bytes each in writeUTF's modified UTF-8, 4 in UTF-8.
        String emoji = app("Emoji", AppManifest.MAX_SOURCE, "📞");
        assertEquals(AppManifest.MAX_SOURCE, ascii.getBytes(StandardCharsets.UTF_8).length);
        AppsSavedData data = new AppsSavedData(AppsSavedData.NAME);
        assertEquals(
            PhoneApps.Installed.NEW,
            data.apps(482913)
                .install(ascii, "Steve", PhoneApps.Origin.STORE));
        assertEquals(
            AppStore.Published.NEW,
            data.store()
                .publish(STEVE, "Steve", emoji, 1));
        data.store()
            .publish(STEVE, "Steve", "--@name Gone\nreturn {}", 2);
        data.store()
            .remove("gone");

        AppsSavedData back = roundTrip(data);
        assertEquals(
            ascii,
            back.apps(482913)
                .get("big")
                .source());
        assertEquals(
            emoji,
            back.store()
                .get("emoji")
                .source());
        assertTrue(
            back.store()
                .removed()
                .contains("gone"));
    }

    @Test
    void computersLastRunSurvivesASaveAndForgetting() throws IOException {
        AppsSavedData data = new AppsSavedData(AppsSavedData.NAME);
        data.seen(731004, 12);
        data.seen(731005, 40);
        data.forget(731005);
        data.store()
            .publish(STEVE, "Steve", "--@name Team Thing\nreturn {}", 3, true);
        data.store()
            .report("team_thing", new UUID(9, 9));
        AppsSavedData back = roundTrip(data);
        assertEquals(12L, back.seenOn(731004));
        assertTrue(
            back.store()
                .get("team_thing")
                .team());
        assertTrue(
            back.store()
                .reported("team_thing", new UUID(9, 9)));
        assertEquals(null, back.seenOn(731005));
    }
}
