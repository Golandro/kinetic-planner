package net.jsmua.kinetic_planner.gui.ribbon.api;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 配置持久化抽象接口 (spec §11.5)。
 *
 * <p>框架不直接依赖 KPConfig, 由 KP 提供 KPConfigRibbonPreferenceStore 实现。
 */
public interface RibbonPreferenceStore {

    /** 获取指定 tab 的显示模式 (未配置返回 empty, 调用方回退到 definition.getDefaultDisplayMode())。 */
    Optional<TabDisplayMode> getTabDisplayMode(ResourceLocation tabId);

    /** 设置指定 tab 的显示模式。 */
    void setTabDisplayMode(ResourceLocation tabId, TabDisplayMode mode);

    /** 获取所有已配置的 tab 显示模式 (用于批量加载)。 */
    Map<ResourceLocation, TabDisplayMode> getAllTabDisplayModes();

    /** 获取 QAT 工具 ID 列表。 */
    List<ResourceLocation> getQatToolIds();

    /** 设置 QAT 工具 ID 列表。 */
    void setQatToolIds(List<ResourceLocation> ids);

    /** 获取上次选中的 tab ID (无返回 empty)。 */
    Optional<ResourceLocation> getSelectedTabId();

    /** 设置上次选中的 tab ID。 */
    void setSelectedTabId(ResourceLocation id);
}
