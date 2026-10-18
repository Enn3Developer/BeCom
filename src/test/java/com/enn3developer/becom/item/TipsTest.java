package com.enn3developer.becom.item;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

/** Long tooltip lines wrap as the Tooltips board's boxes wrap them, so none runs off the screen. */
class TipsTest {

    @Test
    void aShortLineStaysWhole() {
        assertArrayEquals(new String[] { "Uses 128 EU/t at MV" }, Tips.wrap(44, "Uses 128 EU/t at MV"));
    }

    @Test
    void aLongLineWrapsAtSpaces() {
        assertArrayEquals(
            new String[] { "Link it at an ME Security Terminal", "for Max in its range." },
            Tips.wrap(36, "Link it at an ME Security Terminal for Max in its range."));
    }

    @Test
    void colourCodesTakeNoRoom() {
        assertArrayEquals(new String[] { "Bridges up to §b15§7 lines" }, Tips.wrap(24, "Bridges up to §b15§7 lines"));
    }

    @Test
    void aWrappedLineGoesOnInTheColourItBrokeIn() {
        assertArrayEquals(
            new String[] { "§8Two crossed dipoles", "§8on a mast" },
            Tips.wrap(20, "§8Two crossed dipoles on a mast"));
        assertArrayEquals(
            new String[] { "§7Grey then §ewhite", "§elater" },
            Tips.wrap(16, "§7Grey then §ewhite later"));
        assertArrayEquals(new String[] { "§eLit§r then", "plain" }, Tips.wrap(10, "§eLit§r then plain"));
    }

    @Test
    void aWordLongerThanTheLineGetsOneToItself() {
        assertArrayEquals(new String[] { "a", "Wirelessly-linked", "b" }, Tips.wrap(8, "a Wirelessly-linked b"));
    }

    @Test
    void everyLineWrapsOnItsOwn() {
        assertArrayEquals(new String[] { "one two", "three", "four" }, Tips.wrap(8, "one two three", "four"));
    }
}
