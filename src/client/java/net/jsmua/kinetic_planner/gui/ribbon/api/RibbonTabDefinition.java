package net.jsmua.kinetic_planner.gui.ribbon.api;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/**
 * 选项卡定义 (spec §3.2)。
 *
 * <p>Mod 注册的不可变数据。
 */
public interface RibbonTabDefinition {
    /** 唯一标识 (MC ResourceLocation, namespace:path 格式)。 */
    ResourceLocation getId();

    /** 显示名称。 */
    Component getDisplayName();

    /** tab 头图标 (可选)。 */
    Optional<IGuiTexture> getIcon();

    /** 排序优先级 (越小越靠左)。 */
    int getPriority();

    /** 此 tab 包含的工具组。 */
    List<RibbonToolGroupDefinition> getGroups();

    /** 建议的默认显示模式 (用户可覆盖, 但 CONTEXTUAL 由系统控制)。 */
    default TabDisplayMode getDefaultDisplayMode() { return TabDisplayMode.PINNED; }

    /** 是否允许用户隐藏此 tab (核心 tab 可禁止)。 */
    default boolean isUserHideable() { return true; }

    /**
     * 若 getDefaultDisplayMode() == CONTEXTUAL, 返回所属上下文组 ID。
     * 非 CONTEXTUAL tab 返回 Optional.empty()。
     */
    default Optional<ResourceLocation> getContextualGroupId() { return Optional.empty(); }
}

record SimpleRibbonTabDefinition(
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
