package net.jsmua.kinetic_planner.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProviderConfigBindingTest {

    @Test
    void isStrictBoolAcceptsTrueAndFalse() {
        assertTrue(ProviderConfigBinding.isStrictBool("true"));
        assertTrue(ProviderConfigBinding.isStrictBool("false"));
        assertTrue(ProviderConfigBinding.isStrictBool("TRUE"));
        assertTrue(ProviderConfigBinding.isStrictBool("False"));
    }

    @Test
    void isStrictBoolRejectsNonBoolean() {
        assertFalse(ProviderConfigBinding.isStrictBool("1"));
        assertFalse(ProviderConfigBinding.isStrictBool("yes"));
        assertFalse(ProviderConfigBinding.isStrictBool(""));
        assertFalse(ProviderConfigBinding.isStrictBool("tru"));
    }

    @Test
    void anonymousImplementationCanReadConfig() {
        ProviderConfig defaultCfg = ProviderConfig.defaultValue("testmod", "Test Mod");
        ProviderConfigBinding binding = new ProviderConfigBinding() {
            @Override public String modId() { return "testmod"; }
            @Override public ProviderConfig read() { return defaultCfg; }
            @Override public boolean writeEnabled(boolean enabled) { return true; }
            @Override public boolean writeParam(String param, String value) { return true; }
        };
        assertEquals("testmod", binding.modId());
        assertEquals("Test Mod", binding.read().displayName());
        assertTrue(binding.writeEnabled(false));
        assertTrue(binding.writeParam("priority", "5"));
    }
}
