package net.jsmua.kinetic_planner.gui.ribbon.api;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/**
 * SimpleRibbonToolGroupDefinition - RibbonToolGroupDefinition 的简单 record 实现。
 *
 * @param mutualExclusionGroupId 互斥组 ID; 同 tab 内相同 ID 的组共享互斥 toggle 组, empty 表示不互斥
 */
public record SimpleRibbonToolGroupDefinition(
    ResourceLocation id,
    Optional<Component> displayName,
    List<RibbonToolDefinition> tools,
    Optional<ResourceLocation> mutualExclusionGroupId
) implements RibbonToolGroupDefinition {

    /** 三参数构造：不互斥（mutualExclusionGroupId 默认 empty）。 */
    public SimpleRibbonToolGroupDefinition(ResourceLocation id,
                                           Optional<Component> displayName,
                                           List<RibbonToolDefinition> tools) {
        this(id, displayName, tools, Optional.empty());
    }

    @Override public ResourceLocation getId() { return id; }
    @Override public Optional<Component> getDisplayName() { return displayName; }
    @Override public List<RibbonToolDefinition> getTools() { return tools; }
    @Override public Optional<ResourceLocation> getMutualExclusionGroupId() { return mutualExclusionGroupId; }
}
