package net.jsmua.kinetic_planner.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProviderConfigTest {

    @Test
    void defaultValueProducesExpectedFields() {
        ProviderConfig config = ProviderConfig.defaultValue(
            "xaeroworldmap", "Xaero's World Map");
        assertEquals("xaeroworldmap", config.modId());
        assertEquals("Xaero's World Map", config.displayName());
        assertTrue(config.enabled());
        assertEquals(0, config.priority());
        assertEquals(1.0f, config.lineWidthScale(), 1e-6f);
        assertEquals(1.0f, config.alphaScale(), 1e-6f);
        assertFalse(config.dashed());
    }

    @Test
    void withEnabledReturnsNewInstance() {
        ProviderConfig original = ProviderConfig.defaultValue("xaeroworldmap", "Xaero");
        ProviderConfig modified = original.withEnabled(false);
        assertFalse(modified.enabled());
        assertTrue(original.enabled()); // 原 record 不变
    }

    @Test
    void withLineWidthScaleReturnsNewInstance() {
        ProviderConfig original = ProviderConfig.defaultValue("xaeroworldmap", "Xaero");
        ProviderConfig modified = original.withLineWidthScale(2.0f);
        assertEquals(2.0f, modified.lineWidthScale(), 1e-6f);
        assertEquals(1.0f, original.lineWidthScale(), 1e-6f);
    }

    @Test
    void equalsAndHashCodeWork() {
        ProviderConfig c1 = ProviderConfig.defaultValue("xaeroworldmap", "Xaero");
        ProviderConfig c2 = ProviderConfig.defaultValue("xaeroworldmap", "Xaero");
        assertEquals(c1, c2);
        assertEquals(c1.hashCode(), c2.hashCode());
    }
}
