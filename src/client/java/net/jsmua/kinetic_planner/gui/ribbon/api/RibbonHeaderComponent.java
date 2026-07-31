package net.jsmua.kinetic_planner.gui.ribbon.api;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Supplier;

/**
 * Ribbon 头部组件 (spec §6.1)。
 *
 * <p>原名 RibbonTabBarComponent, 重命名为 RibbonHeaderComponent 避免与 LDLib2 Tab/TabView 混淆。
 */
public interface RibbonHeaderComponent {
    /** 唯一标识 (MC ResourceLocation)。 */
    ResourceLocation getId();

    /** 放置位置。 */
    Placement getPlacement();

    /** 排序优先级 (同侧内, 越小越靠外)。 */
    int getPriority();

    /** 创建 UI 元素。 */
    UIElement createElement();

    enum Placement { LEADING, TRAILING }
}
