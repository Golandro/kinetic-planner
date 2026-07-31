package net.jsmua.kinetic_planner.gui.ribbon.api;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class RibbonToolDefinitionTest {

    @Test
    void simpleRecordStoresAllFields() {
        RibbonCommand cmd = () -> {};
        ButtonAction action = new ButtonAction(cmd, false);
        Component name = Component.literal("Pan");
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("kp", "tool_pan");

        RibbonToolDefinition def = new SimpleRibbonToolDefinition(
            id, name, Optional.empty(), Optional.empty(), Optional.empty(),
            ToolSize.LARGE, action);

        assertEquals(id, def.getId());
        assertEquals(name, def.getDisplayName());
        assertTrue(def.getIcon().isEmpty());
        assertTrue(def.getTooltip().isEmpty());
        assertTrue(def.getShortcutLabel().isEmpty());
        assertEquals(ToolSize.LARGE, def.getSize());
        assertEquals(action, def.getAction());
    }

    @Test
    void defaultOverflowWeightIs100() {
        RibbonToolDefinition def = new SimpleRibbonToolDefinition(
            ResourceLocation.fromNamespaceAndPath("kp", "t"),
            Component.literal("T"),
            Optional.empty(), Optional.empty(), Optional.empty(),
            ToolSize.SMALL,
            new ButtonAction(() -> {}, false));
        assertEquals(100, def.getOverflowWeight(),
            "默认溢出权重必须为 RibbonConstants.DEFAULT_OVERFLOW_WEIGHT (100)");
    }

    @Test
    void defaultKeyTipsEmpty() {
        RibbonToolDefinition def = new SimpleRibbonToolDefinition(
            ResourceLocation.fromNamespaceAndPath("kp", "t"),
            Component.literal("T"),
            Optional.empty(), Optional.empty(), Optional.empty(),
            ToolSize.SMALL,
            new ButtonAction(() -> {}, false));
        assertTrue(def.getKeyTips().isEmpty(),
            "首版 KeyTips 必须返回 Optional.empty() (Phase 2+ 预留字段)");
    }
}
