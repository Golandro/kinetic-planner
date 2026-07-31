package net.jsmua.kinetic_planner.gui.ribbon.api;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/**
 * SimpleRibbonToolGroupDefinition - RibbonToolGroupDefinition 的简单 record 实现。
 */
public record SimpleRibbonToolGroupDefinition(
    ResourceLocation id,
    Optional<Component> displayName,
    List<RibbonToolDefinition> tools
) implements RibbonToolGroupDefinition {
    @Override public ResourceLocation getId() { return id; }
    @Override public Optional<Component> getDisplayName() { return displayName; }
    @Override public List<RibbonToolDefinition> getTools() { return tools; }
}
