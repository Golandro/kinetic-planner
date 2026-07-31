package net.jsmua.kinetic_planner.gui.ribbon.registry;

import net.jsmua.kinetic_planner.gui.ribbon.api.RibbonHeaderComponent.Placement;
import net.jsmua.kinetic_planner.gui.ribbon.api.RibbonHeaderComponent;
import net.jsmua.kinetic_planner.gui.ribbon.api.RibbonTabDefinition;
import net.jsmua.kinetic_planner.gui.ribbon.api.SimpleRibbonTabDefinition;
import net.jsmua.kinetic_planner.gui.ribbon.api.SimpleRibbonHeaderComponent;
import net.jsmua.kinetic_planner.gui.ribbon.api.TabDisplayMode;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class RibbonRegistryTest {

    @AfterEach
    void resetRegistry() {
        // 每个测试后重置注册表，避免跨测试污染
        RibbonRegistry.resetForTest();
    }

    private RibbonTabDefinition makeTab(String path, int priority) {
        return new SimpleRibbonTabDefinition(
            ResourceLocation.fromNamespaceAndPath("kp", path),
            Component.literal(path),
            Optional.empty(),
            priority,
            List.of(),
            TabDisplayMode.PINNED,
            true,
            Optional.empty()
        );
    }

    @Test
    void registerTabBeforeFreezeSucceeds() {
        var tab = makeTab("tools", 100);
        RibbonRegistry.registerTab(tab.getId(), tab);
        var all = RibbonRegistry.getTabsSortedByPriority();
        assertEquals(1, all.size());
        assertEquals(tab, all.get(0));
    }

    @Test
    void registerTabAfterFreezeThrows() {
        RibbonRegistry.freeze();
        assertTrue(RibbonRegistry.isFrozen());
        var tab = makeTab("tools", 100);
        assertThrows(IllegalStateException.class,
            () -> RibbonRegistry.registerTab(tab.getId(), tab),
            "冻结后注册必须抛 IllegalStateException");
    }

    @Test
    void duplicateIdThrows() {
        var tab = makeTab("tools", 100);
        RibbonRegistry.registerTab(tab.getId(), tab);
        assertThrows(IllegalStateException.class,
            () -> RibbonRegistry.registerTab(tab.getId(), tab),
            "重复 ID 必须抛 IllegalStateException");
    }

    @Test
    void getTabsSortedByPriorityAscending() {
        RibbonRegistry.registerTab(ResourceLocation.fromNamespaceAndPath("kp", "c"), makeTab("c", 300));
        RibbonRegistry.registerTab(ResourceLocation.fromNamespaceAndPath("kp", "a"), makeTab("a", 100));
        RibbonRegistry.registerTab(ResourceLocation.fromNamespaceAndPath("kp", "b"), makeTab("b", 200));

        var sorted = RibbonRegistry.getTabsSortedByPriority();
        assertEquals(3, sorted.size());
        assertEquals("a", sorted.get(0).getId().getPath());
        assertEquals("b", sorted.get(1).getId().getPath());
        assertEquals("c", sorted.get(2).getId().getPath());
    }

    @Test
    void resetForTestClearsRegistryAndUnfreezes() {
        RibbonRegistry.registerTab(ResourceLocation.fromNamespaceAndPath("kp", "a"), makeTab("a", 100));
        RibbonRegistry.freeze();
        assertTrue(RibbonRegistry.isFrozen());

        RibbonRegistry.resetForTest();
        assertFalse(RibbonRegistry.isFrozen(), "resetForTest 必须解除冻结");
        assertTrue(RibbonRegistry.getTabsSortedByPriority().isEmpty(),
            "resetForTest 必须清空注册表");
    }

    @Test
    void headerComponentsSortedByPriorityPerPlacement() {
        var lead1 = new SimpleRibbonHeaderComponent(
            ResourceLocation.fromNamespaceAndPath("kp", "l1"),
            Placement.LEADING, 100, () -> null);
        var lead2 = new SimpleRibbonHeaderComponent(
            ResourceLocation.fromNamespaceAndPath("kp", "l2"),
            Placement.LEADING, 50, () -> null);
        var trail1 = new SimpleRibbonHeaderComponent(
            ResourceLocation.fromNamespaceAndPath("kp", "t1"),
            Placement.TRAILING, 100, () -> null);

        RibbonRegistry.registerHeaderComponent(lead1.getId(), lead1);
        RibbonRegistry.registerHeaderComponent(lead2.getId(), lead2);
        RibbonRegistry.registerHeaderComponent(trail1.getId(), trail1);

        var leading = RibbonRegistry.getHeaderComponents(Placement.LEADING);
        var trailing = RibbonRegistry.getHeaderComponents(Placement.TRAILING);
        assertEquals(2, leading.size());
        assertEquals(1, trailing.size());
        assertEquals("l2", leading.get(0).getId().getPath(), "priority=50 应排在 priority=100 前");
        assertEquals("l1", leading.get(1).getId().getPath());
    }
}
