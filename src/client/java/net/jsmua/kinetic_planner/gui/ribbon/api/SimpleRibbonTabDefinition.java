package net.jsmua.kinetic_planner.gui.ribbon.api;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/**
 * SimpleRibbonTabDefinition - RibbonTabDefinition 的简单 record 实现。
 */
public record SimpleRibbonTabDefinition(
    ResourceLocation id,
    Component displayName,
    Optional<IGuiTexture> icon,
    int priority,
    List<RibbonToolGroupDefinition> groups,
    TabDisplayMode defaultDisplayMode,
    boolean userHideable,
    Optional<ResourceLocation> contextualGroupId
) implements RibbonTabDefinition {
    @Override public ResourceLocation getId() { return id; }
    @Override public Component getDisplayName() { return displayName; }
    @Override public Optional<IGuiTexture> getIcon() { return icon; }
    @Override public int getPriority() { return priority; }
    @Override public List<RibbonToolGroupDefinition> getGroups() { return groups; }
    @Override public TabDisplayMode getDefaultDisplayMode() { return defaultDisplayMode; }
    @Override public boolean isUserHideable() { return userHideable; }
    @Override public Optional<ResourceLocation> getContextualGroupId() { return contextualGroupId; }
}
