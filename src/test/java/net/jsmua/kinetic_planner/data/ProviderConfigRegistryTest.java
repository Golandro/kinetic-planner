package net.jsmua.kinetic_planner.data;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ProviderConfigRegistryTest {

    @AfterEach
    void cleanup() {
        ProviderConfigRegistry.clear();
    }

    @Test
    void registerAddsModId() {
        ProviderConfig config = ProviderConfig.defaultValue("xaeroworldmap", "Xaero");
        ProviderConfigRegistry.register("xaeroworldmap", config);
        assertTrue(ProviderConfigRegistry.getRegisteredModIds().contains("xaeroworldmap"));
    }

    @Test
    void getDefaultReturnsRegisteredConfig() {
        ProviderConfig config = ProviderConfig.defaultValue("journeymap", "JourneyMap");
        ProviderConfigRegistry.register("journeymap", config);
        ProviderConfig got = ProviderConfigRegistry.getDefault("journeymap");
        assertNotNull(got);
        assertEquals("journeymap", got.modId());
    }

    @Test
    void getDefaultReturnsNullForUnknownModId() {
        ProviderConfig got = ProviderConfigRegistry.getDefault("unknown");
        assertNull(got);
    }

    @Test
    void getRegisteredModIdsIsImmutableSnapshot() {
        ProviderConfigRegistry.register("xaeroworldmap",
            ProviderConfig.defaultValue("xaeroworldmap", "Xaero"));
        Set<String> ids = ProviderConfigRegistry.getRegisteredModIds();
        assertEquals(1, ids.size());
        // 注册更多不影响已返回的快照
        ProviderConfigRegistry.register("journeymap",
            ProviderConfig.defaultValue("journeymap", "JourneyMap"));
        assertEquals(1, ids.size());
    }
}
