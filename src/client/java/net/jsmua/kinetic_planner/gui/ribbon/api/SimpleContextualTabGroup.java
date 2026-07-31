package net.jsmua.kinetic_planner.gui.ribbon.api;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/**
 * SimpleContextualTabGroup - ContextualTabGroup 的简单 record 实现。
 */
public record SimpleContextualTabGroup(
    ResourceLocation id,
    Component displayName,
    Optional<IGuiTexture> icon,
    int priority,
    Optional<Integer> accentColor,
    List<RibbonTabDefinition> tabs,
    TabDisplayMode activeSubMode
) implements ContextualTabGroup {
    @Override public ResourceLocation getId() { return id; }
    @Override public Component getDisplayName() { return displayName; }
    @Override public Optional<IGuiTexture> getIcon() { return icon; }
    @Override public int getPriority() { return priority; }
    @Override public Optional<Integer> getAccentColor() { return accentColor; }
    @Override public List<RibbonTabDefinition> getTabs() { return tabs; }
    @Override public TabDisplayMode getActiveSubMode() { return activeSubMode; }
}
