package com.enn3developer.becom.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StringTranslate;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** GT's scanner translates a machine's lines on the server, WAILA on the client: both must read the same. */
class InfoLinesTest {

    @BeforeAll
    static void translations() {
        String lang = """
            becom.test.plain=Plain
            becom.test.args=%s at %s
            becom.test.level=High
            becom.test.detail=Detail %s
            """;
        StringTranslate.inject(new ByteArrayInputStream(lang.getBytes(StandardCharsets.UTF_8)));
    }

    private static InfoLines sample() {
        return new InfoLines().add("becom.test.plain")
            .add("becom.test.args", 12, new InfoLines.Key("becom.test.level"))
            .detail("becom.test.detail", 3);
    }

    @Test
    void theScannerGetsEveryLine() {
        assertEquals(List.of("Plain", "12 at High", "Detail 3"), sample().translate());
    }

    @Test
    void wailaGetsTheSameLinesBarTheDetails() {
        NBTTagCompound tag = new NBTTagCompound();
        sample().write(tag);
        List<String> tip = new ArrayList<>();
        InfoLines.read(tag, tip);
        assertEquals(List.of("Plain", "12 at High"), tip);
    }

    @Test
    void aTagWithoutLinesAddsNothing() {
        List<String> tip = new ArrayList<>();
        InfoLines.read(new NBTTagCompound(), tip);
        InfoLines.read(null, tip);
        assertEquals(List.of(), tip);
    }
}
