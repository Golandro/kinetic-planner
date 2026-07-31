package net.jsmua.kinetic_planner.gui.editor.ribbon;

import net.jsmua.kinetic_planner.config.IKPConfig;
import net.jsmua.kinetic_planner.gui.ribbon.api.RibbonPreferenceStore;
import net.jsmua.kinetic_planner.gui.ribbon.api.TabDisplayMode;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KPConfigRibbonPreferenceStoreTest {

    @Test
    void getTabDisplayModeDelegatesToConfig() {
        var config = mock(IKPConfig.class);
        var tabId = ResourceLocation.fromNamespaceAndPath("kp", "tools");
        when(config.getRibbonTabDisplayMode("kp:tools")).thenReturn("FLOATING");

        var store = new KPConfigRibbonPreferenceStore(config);
        assertEquals(Optional.of(TabDisplayMode.FLOATING), store.getTabDisplayMode(tabId));
    }

    @Test
    void getTabDisplayModeEmptyWhenConfigReturnsNull() {
        var config = mock(IKPConfig.class);
        when(config.getRibbonTabDisplayMode(anyString())).thenReturn(null);

        var store = new KPConfigRibbonPreferenceStore(config);
        assertTrue(store.getTabDisplayMode(ResourceLocation.fromNamespaceAndPath("kp", "x")).isEmpty());
    }

    @Test
    void setTabDisplayModeDelegatesToConfig() {
        var config = mock(IKPConfig.class);
        var store = new KPConfigRibbonPreferenceStore(config);

        store.setTabDisplayMode(ResourceLocation.fromNamespaceAndPath("kp", "tools"), TabDisplayMode.HIDDEN);

        verify(config).setRibbonTabDisplayMode("kp:tools", "HIDDEN");
    }

    @Test
    void qatToolIdsRoundTrip() {
        var config = mock(IKPConfig.class);
        var ids = List.of("kp:tool_pan", "kp:tool_select");
        when(config.getRibbonQatToolIds()).thenReturn(ids);

        var store = new KPConfigRibbonPreferenceStore(config);
        var result = store.getQatToolIds();
        assertEquals(2, result.size());
        assertEquals(ResourceLocation.fromNamespaceAndPath("kp", "tool_pan"), result.get(0));

        store.setQatToolIds(List.of(ResourceLocation.fromNamespaceAndPath("kp", "tool_line")));
        verify(config).setRibbonQatToolIds(List.of("kp:tool_line"));
    }

    @Test
    void selectedTabIdRoundTrip() {
        var config = mock(IKPConfig.class);
        when(config.getRibbonSelectedTab()).thenReturn("kp:tools");

        var store = new KPConfigRibbonPreferenceStore(config);
        assertEquals(Optional.of(ResourceLocation.fromNamespaceAndPath("kp", "tools")),
            store.getSelectedTabId());

        store.setSelectedTabId(ResourceLocation.fromNamespaceAndPath("kp", "file"));
        verify(config).setRibbonSelectedTab("kp:file");
    }

    @Test
    void invalidTabDisplayModeStringReturnsEmpty() {
        var config = mock(IKPConfig.class);
        when(config.getRibbonTabDisplayMode(anyString())).thenReturn("INVALID_MODE");

        var store = new KPConfigRibbonPreferenceStore(config);
        assertTrue(store.getTabDisplayMode(ResourceLocation.fromNamespaceAndPath("kp", "x")).isEmpty(),
            "无效的 TabDisplayMode 字符串应返回 empty (容错)");
    }

    @Test
    void getAllTabDisplayModesReturnsEmptyByDefault() {
        var config = mock(IKPConfig.class);
        // IKPConfig 当前不暴露 getAll 批量接口, store 用空 Map 兜底
        var store = new KPConfigRibbonPreferenceStore(config);
        assertTrue(store.getAllTabDisplayModes().isEmpty(),
            "未配置任何 tab mode 时, getAllTabDisplayModes 返回空 Map");
    }
}
