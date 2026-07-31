package net.jsmua.kinetic_planner.gui.ribbon.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TabDisplayModeTest {

    @Test
    void enumHasFourValues() {
        TabDisplayMode[] values = TabDisplayMode.values();
        assertEquals(4, values.length, "TabDisplayMode 必须有 4 个值 (PINNED/FLOATING/HIDDEN/CONTEXTUAL)");
        assertArrayEquals(
            new TabDisplayMode[]{
                TabDisplayMode.PINNED,
                TabDisplayMode.FLOATING,
                TabDisplayMode.HIDDEN,
                TabDisplayMode.CONTEXTUAL
            },
            values,
            "必须包含 PINNED/FLOATING/HIDDEN/CONTEXTUAL 且按此顺序");
    }

    @Test
    void valueOfAcceptsAllFour() {
        assertEquals(TabDisplayMode.PINNED, TabDisplayMode.valueOf("PINNED"));
        assertEquals(TabDisplayMode.FLOATING, TabDisplayMode.valueOf("FLOATING"));
        assertEquals(TabDisplayMode.HIDDEN, TabDisplayMode.valueOf("HIDDEN"));
        assertEquals(TabDisplayMode.CONTEXTUAL, TabDisplayMode.valueOf("CONTEXTUAL"));
    }
}
