package net.jsmua.kinetic_planner.gui.ribbon.api;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/**
 * 上下文选项卡组 (spec §10.2)。
 *
 * <p>一组基于视图上下文激活的选项卡。激活时组内所有 tab 头出现; 停用时消失。
 *
 * <p><b>关键约束:</b> CONTEXTUAL 模式只用于视图切换, 不用于对象选择。
 */
public interface ContextualTabGroup {
    /** 唯一标识 (MC ResourceLocation)。 */
    ResourceLocation getId();

    /** 组显示名称 (用于组头部标签)。 */
    Component getDisplayName();

    /** 组头部图标 (可选)。 */
    Optional<IGuiTexture> getIcon();

    /** 排序优先级 (多个上下文组同时激活时, 越小越靠左)。 */
    int getPriority();

    /** 组的视觉强调色 (可选, RGB 值, 用于组头部底色与 tab 底色)。 */
    Optional<Integer> getAccentColor();

    /** 此组包含的选项卡定义 (每个 tab 的 getDefaultDisplayMode() 应返回 CONTEXTUAL)。 */
    List<RibbonTabDefinition> getTabs();

    /** 激活时 tab 内容的默认显示子模式 (PINNED 或 FLOATING)。 */
    default TabDisplayMode getActiveSubMode() { return TabDisplayMode.PINNED; }
}
