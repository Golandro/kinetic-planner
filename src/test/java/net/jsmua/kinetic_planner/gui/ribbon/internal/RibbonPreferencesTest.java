package net.jsmua.kinetic_planner.gui.ribbon.internal;

import net.jsmua.kinetic_planner.gui.ribbon.api.RibbonPreferenceStore;
import net.jsmua.kinetic_planner.gui.ribbon.api.TabDisplayMode;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class RibbonPreferencesTest {

    /** In-memory stub 实现, 用于测试 RibbonPreferences 读写逻辑。 */
    private static class InMemoryStore implements RibbonPreferenceStore {
        final Map<ResourceLocation, TabDisplayMode> tabModes = new HashMap<>();
        List<ResourceLocation> qatIds = new ArrayList<>();
        ResourceLocation selectedTabId = null;

        @Override
        public Optional<TabDisplayMode> getTabDisplayMode(ResourceLocation tabId) {
            return Optional.ofNullable(tabModes.get(tabId));
        }

        @Override
        public void setTabDisplayMode(ResourceLocation tabId, TabDisplayMode mode) {
            tabModes.put(tabId, mode);
        }

        @Override
        public Map<ResourceLocation, TabDisplayMode> getAllTabDisplayModes() {
            return new HashMap<>(tabModes);
        }

        @Override
        public List<ResourceLocation> getQatToolIds() {
            return new ArrayList<>(qatIds);
        }

        @Override
        public void setQatToolIds(List<ResourceLocation> ids) {
            this.qatIds = new ArrayList<>(ids);
        }

        @Override
        public Optional<ResourceLocation> getSelectedTabId() {
            return Optional.ofNullable(selectedTabId);
        }

        @Override
        public void setSelectedTabId(ResourceLocation id) {
            this.selectedTabId = id;
        }
    }

    @Test
    void loadReadsAllPreferencesFromStore() {
        var store = new InMemoryStore();
        var tabId = ResourceLocation.fromNamespaceAndPath("kp", "tools");
        var qatId = ResourceLocation.fromNamespaceAndPath("kp", "tool_pan");
        store.tabModes.put(tabId, TabDisplayMode.FLOATING);
        store.qatIds.add(qatId);
        store.selectedTabId = tabId;

        var prefs = RibbonPreferences.load(store);

        assertEquals(TabDisplayMode.FLOATING, prefs.getTabDisplayMode(tabId).orElseThrow());
        assertEquals(1, prefs.getQatToolIds().size());
        assertEquals(qatId, prefs.getQatToolIds().get(0));
        assertEquals(tabId, prefs.getSelectedTabId().orElseThrow());
    }

    @Test
    void saveWritesAllPreferencesToStore() {
        var store = new InMemoryStore();
        var prefs = new RibbonPreferences(
            new HashMap<>(),
            new ArrayList<>(),
            null);

        var tabId = ResourceLocation.fromNamespaceAndPath("kp", "file");
        prefs.setTabDisplayMode(tabId, TabDisplayMode.HIDDEN);
        prefs.addQatToolId(ResourceLocation.fromNamespaceAndPath("kp", "tool_select"));
        prefs.setSelectedTabId(tabId);

        prefs.save(store);

        assertEquals(TabDisplayMode.HIDDEN, store.tabModes.get(tabId));
        assertEquals(1, store.qatIds.size());
        assertEquals(tabId, store.selectedTabId);
    }

    @Test
    void roundTripPreservesData() {
        var store1 = new InMemoryStore();
        var tabId = ResourceLocation.fromNamespaceAndPath("kp", "view");
        store1.tabModes.put(tabId, TabDisplayMode.CONTEXTUAL);
        store1.qatIds.add(ResourceLocation.fromNamespaceAndPath("kp", "tool_pan"));
        store1.qatIds.add(ResourceLocation.fromNamespaceAndPath("kp", "tool_select"));
        store1.selectedTabId = tabId;

        var prefs = RibbonPreferences.load(store1);
        var store2 = new InMemoryStore();
        prefs.save(store2);

        assertEquals(store1.tabModes, store2.tabModes);
        assertEquals(store1.qatIds, store2.qatIds);
        assertEquals(store1.selectedTabId, store2.selectedTabId);
    }

    @Test
    void unknownTabReturnsEmptyOptional() {
        var store = new InMemoryStore();
        var prefs = RibbonPreferences.load(store);
        assertTrue(prefs.getTabDisplayMode(ResourceLocation.fromNamespaceAndPath("kp", "unknown")).isEmpty());
    }
}
