package net.jsmua.kinetic_planner.gui.ribbon.api;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Supplier;

/**
 * SimpleRibbonHeaderComponent - RibbonHeaderComponent 的简单 record 实现。
 */
public record SimpleRibbonHeaderComponent(
    ResourceLocation id,
    Placement placement,
    int priority,
    Supplier<UIElement> elementSupplier
) implements RibbonHeaderComponent {
    @Override public ResourceLocation getId() { return id; }
    @Override public Placement getPlacement() { return placement; }
    @Override public int getPriority() { return priority; }
    @Override public UIElement createElement() { return elementSupplier.get(); }
}
