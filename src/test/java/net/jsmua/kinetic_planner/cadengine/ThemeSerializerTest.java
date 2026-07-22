package net.jsmua.kinetic_planner.cadengine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ThemeSerializerTest {

    @Test
    void roundTripDefaultThemePreservesAllFields() {
        Theme original = Theme.defaultValue();
        String json = ThemeSerializer.serialize(original);
        Theme restored = ThemeSerializer.deserialize(json);

        assertEquals(original.name(), restored.name());
        assertEquals(original.track().width(), restored.track().width(), 1e-6f);
        assertEquals(original.track().dashed(), restored.track().dashed());
        assertEquals(original.track().alpha(), restored.track().alpha(), 1e-6f);
        assertEquals(original.node().width(), restored.node().width(), 1e-6f);
        assertEquals(original.edgePoint().width(), restored.edgePoint().width(), 1e-6f);
        assertEquals(original.layers().tracks(), restored.layers().tracks());
        assertEquals(original.layers().nodes(), restored.layers().nodes());
        assertEquals(original.layers().edgePoints(), restored.layers().edgePoints());
        assertEquals(original.global().constantScreenLineWidth(), restored.global().constantScreenLineWidth());
        assertEquals(original.global().fixedScreenLineWidthPx(), restored.global().fixedScreenLineWidthPx(), 1e-6f);
        assertEquals(original.global().minZoomBlocksPerPixel(), restored.global().minZoomBlocksPerPixel(), 1e-6f);
        assertEquals(original.global().maxZoomBlocksPerPixel(), restored.global().maxZoomBlocksPerPixel(), 1e-6f);
    }

    @Test
    void roundTripCustomThemePreservesFields() {
        Theme custom = new Theme(
            "custom",
            new Theme.GeometryStyle(5.0f, true, 0.8f),
            new Theme.GeometryStyle(8.0f, false, 1.0f),
            new Theme.GeometryStyle(6.0f, false, 0.5f),
            new Theme.LayerVisibility(false, true, false),
            new Theme.GlobalStyle(0.1f, 10.0f, false, 3.0f)
        );
        String json = ThemeSerializer.serialize(custom);
        Theme restored = ThemeSerializer.deserialize(json);

        assertEquals("custom", restored.name());
        assertEquals(5.0f, restored.track().width(), 1e-6f);
        assertTrue(restored.track().dashed());
        assertEquals(0.8f, restored.track().alpha(), 1e-6f);
        assertFalse(restored.layers().tracks());
        assertFalse(restored.global().constantScreenLineWidth());
    }

    @Test
    void serializedJsonContainsExpectedFields() {
        Theme theme = Theme.defaultValue();
        String json = ThemeSerializer.serialize(theme);
        assertNotNull(json);
        assertTrue(json.contains("\"name\""));
        assertTrue(json.contains("\"track\""));
        assertTrue(json.contains("\"width\""));
        assertTrue(json.contains("\"layers\""));
        assertTrue(json.contains("\"global\""));
    }
}
