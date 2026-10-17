package com.enn3developer.becom.logic.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class AppLogicTest {

    /** A Lua array, as OpenComputers hands it to Java: keyed by doubles from 1. */
    private static Map<Object, Object> array(Object... items) {
        Map<Object, Object> t = new HashMap<>();
        for (int i = 0; i < items.length; i++) t.put((double) (i + 1), items[i]);
        return t;
    }

    private static Map<Object, Object> widget(Object... kv) {
        Map<Object, Object> t = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) t.put(kv[i], kv[i + 1]);
        return t;
    }

    @Test
    void parsesEachWidgetKind() {
        List<AppTree.Widget> w = AppTree.parse(
            array(
                widget("type", "heading", "text", "Main reactor"),
                widget("type", "value", "label", "Output", "value", 8192.0, "level", "high"),
                widget("type", "progress", "label", "Heat", "value", 1.7),
                widget("type", "toggle", "id", "run", "label", "Running", "on", true),
                widget("type", "button", "id", "scram", "label", "SCRAM", "style", "danger"),
                widget("type", "input", "id", "q", "hint", "Search items")));
        assertEquals(6, w.size());
        assertEquals(
            AppTree.Kind.HEADING,
            w.get(0)
                .kind());
        assertEquals(
            "8192",
            w.get(1)
                .value());
        assertEquals(
            AppTree.Level.HIGH,
            w.get(1)
                .level());
        assertEquals(
            1.0,
            w.get(2)
                .fraction());
        assertEquals(
            "true",
            w.get(3)
                .value());
        assertEquals(
            AppTree.Style.DANGER,
            w.get(4)
                .style());
        assertEquals(
            "Search items",
            w.get(5)
                .value());
    }

    @Test
    void listsFlattenAndDoNotNest() {
        Map<Object, Object> inner = widget("type", "list", "rows", array(widget("type", "text", "text", "deep")));
        List<AppTree.Widget> w = AppTree.parse(
            array(
                widget(
                    "type",
                    "list",
                    "rows",
                    array(widget("type", "text", "text", "a"), widget("type", "text", "text", "b"), inner))));
        assertEquals(2, w.size());
    }

    @Test
    void malformedWidgetsAreLeftOutAndTextIsCapped() {
        List<AppTree.Widget> w = AppTree.parse(
            array(widget("type", "rocket"), "not a table", widget("type", "text", "text", "x".repeat(500) + "§c")));
        assertEquals(1, w.size());
        assertEquals(
            AppTree.MAX_TEXT,
            w.get(0)
                .label()
                .length());
    }

    @Test
    void theWidgetCountIsCapped() {
        Object[] many = new Object[AppTree.MAX_WIDGETS + 50];
        for (int i = 0; i < many.length; i++) many[i] = widget("type", "text", "text", "row");
        assertEquals(
            AppTree.MAX_WIDGETS,
            AppTree.parse(array(many))
                .size());
    }

    @Test
    void manifestReadsTheLeadingComments() {
        AppManifest m = AppManifest.parse(
            "--@name Main Reactor!\n--@version 1.2\n--@accent #2a8a4a\n-- a plain comment\n--@about Hot.\nlocal x = 1\n--@name Late");
        assertEquals("Main Reactor!", m.name());
        assertEquals("main_reactor", m.id());
        assertEquals("1.2", m.version());
        assertEquals(0x2a8a4a, m.accent());
        assertEquals("Hot.", m.about());
    }

    @Test
    void anAppNeedsAName() {
        assertNull(AppManifest.parse("local x = 1"));
        assertNull(AppManifest.parse("--@version 2\n"));
    }

    @Test
    void anIconIsSixtyFourHexDigits() {
        assertEquals(
            "",
            AppManifest.parse("--@name A\n--@icon 12zz")
                .icon());
        assertEquals(
            64,
            AppManifest.parse("--@name A\n--@icon " + "0f".repeat(32))
                .icon()
                .length());
    }

    @Test
    void messagesTakeTheirSizeAtTheSlowerEndsRate() {
        // 8 KB at 4 KB/s: 2 seconds.
        assertEquals(40, DataLink.ticks(8 * 1024, DataLink.rate(4, 256)));
        assertEquals(1, DataLink.ticks(10, 256));
        assertEquals(-1, DataLink.ticks(10, DataLink.rate(0, 256)));
        assertTrue(DataLink.ticks(8 * 1024, 256) >= 1);
    }

    @Test
    void aTableThatHoldsItselfEnds() {
        Map<Object, Object> loop = new HashMap<>();
        loop.put(1.0, loop);
        loop.put(2.0, Map.of("type", "text", "text", "still here"));
        List<AppTree.Widget> widgets = AppTree.parse(loop);
        assertTrue(widgets.size() <= AppTree.MAX_WIDGETS);
        assertEquals(
            "still here",
            widgets.get(0)
                .label());
    }
}
