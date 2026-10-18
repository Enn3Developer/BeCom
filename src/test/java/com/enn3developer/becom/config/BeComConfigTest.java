package com.enn3developer.becom.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.nio.file.Files;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.enn3developer.becom.logic.Tunables;

/** A server acts on its config file; a client never does, so a bad file can't stop it starting. */
class BeComConfigTest {

    @BeforeAll
    static void minecraftHome(@TempDir File home) throws Exception {
        // Forge's Configuration asks FML where Minecraft lives; outside a game nobody has told it.
        java.lang.reflect.Field field = Class.forName("cpw.mods.fml.relauncher.FMLInjectionData")
            .getDeclaredField("minecraftHome");
        field.setAccessible(true);
        if (field.get(null) == null) field.set(null, home);
    }

    @AfterEach
    void reset() {
        BeComConfig.useDefaults();
    }

    @Test
    void aBadValueStopsOnlyAServerStartingWithIt(@TempDir File dir) throws Exception {
        File file = new File(dir, "becom.cfg");
        // A rule no single option's range can hold: video taking less than voice.
        Files.write(file.toPath(), "wired {\n    I:voiceRate=20\n    I:videoRate=10\n}\n".getBytes());
        // Loading happens on every side, a client's included: it mustn't throw.
        assertDoesNotThrow(() -> BeComConfig.load(file));
        // A server about to use the file stops, naming the key.
        IllegalStateException e = assertThrows(IllegalStateException.class, BeComConfig::useFileValues);
        assertEquals(
            true,
            e.getMessage()
                .contains("videoRate"),
            e.getMessage());
    }

    @Test
    void cappedValuesAreClampedIntoTheirRange(@TempDir File dir) throws Exception {
        File file = new File(dir, "becom.cfg");
        Files.write(
            file.toPath(),
            "wired {\n    I:cableReachFibre=100000\n}\nantennas {\n    I:antennaMastMax=4000\n}\n".getBytes());
        BeComConfig.load(file);
        BeComConfig.useFileValues();
        assertEquals(
            Tunables.MAX_CABLE,
            BeComConfig.get()
                .cableReachFibre());
        assertEquals(
            Tunables.MAX_COLUMN,
            BeComConfig.get()
                .antennaMastMax());
    }

    @Test
    void zeroWhereAtLeastOneIsNeededIsClampedNotFatal(@TempDir File dir) throws Exception {
        File file = new File(dir, "becom.cfg");
        Files.write(file.toPath(), "calls {\n    I:ringSeconds=0\n}\nradio {\n    I:surveyStep=0\n}\n".getBytes());
        BeComConfig.load(file);
        BeComConfig.useFileValues();
        assertEquals(
            1,
            BeComConfig.get()
                .ringSeconds());
        assertEquals(
            1,
            BeComConfig.get()
                .surveyStep());
        // The file shows the range the server holds the option to.
        String text = new String(Files.readAllBytes(file.toPath()));
        assertEquals(true, text.contains("range: 1 ~"), text);
    }

    @Test
    void aGoodFileIsUsed(@TempDir File dir) {
        File file = new File(dir, "becom.cfg");
        BeComConfig.load(file);
        BeComConfig.useFileValues();
        assertEquals(Tunables.defaults(), BeComConfig.get());
    }

    @Test
    void aNewerFileIsLeftAloneAndRefusedOnlyWhenAServerStarts(@TempDir File dir) throws Exception {
        File file = new File(dir, "becom.cfg");
        String newer = "general {\n    I:formatVersion=99\n}\nwired {\n    I:cableReachPhone=12\n}\n";
        Files.write(file.toPath(), newer.getBytes());
        assertDoesNotThrow(() -> BeComConfig.load(file));
        assertEquals(
            true,
            BeComConfig.fileProblem()
                .contains("newer"));
        assertThrows(IllegalStateException.class, BeComConfig::useFileValues);
        // Never rewritten by a BeCom that doesn't understand it.
        assertEquals(newer, new String(Files.readAllBytes(file.toPath())));
    }

    @Test
    void everyTunableIsInTheFile(@TempDir File dir) throws Exception {
        File file = new File(dir, "becom.cfg");
        BeComConfig.load(file);
        String text = new String(Files.readAllBytes(file.toPath()));
        for (java.lang.reflect.RecordComponent c : Tunables.class.getRecordComponents()) {
            assertEquals(true, text.contains(":" + c.getName() + "="), c.getName() + " is missing from the file");
        }
    }
}
