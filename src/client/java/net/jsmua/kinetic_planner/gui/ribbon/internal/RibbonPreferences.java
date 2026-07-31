package net.jsmua.kinetic_planner.gui.ribbon.internal;

import net.jsmua.kinetic_planner.gui.ribbon.api.RibbonPreferenceStore;
import net.jsmua.kinetic_planner.gui.ribbon.api.TabDisplayMode;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

/**
 * Ribbon 偏好读写器 (spec §3.5)。
 *
 * <p>封装 RibbonPreferenceStore 的读写逻辑, 提供 in-memory 状态 + 一次性 load/save。
 * 测试用 in-memory stub store 验证读写往返, 不依赖真实 KPConfig。
 */
public final class RibbonPreferences {

    private final Map<ResourceLocation, TabDisplayMode> tabDisplayModes;
    private final List<ResourceLocation> qatToolIds;
    private ResourceLocation selectedTabId;

    public RibbonPreferences(
        Map<ResourceLocation, TabDisplayMode> tabDisplayModes,
        List<ResourceLocation> qatToolIds,
        ResourceLocation selectedTabId) {
        this.tabDisplayModes = new HashMap<>(tabDisplayModes);
        this.qatToolIds = new ArrayList<>(qatToolIds);
        this.selectedTabId = selectedTabId;
    }

    /** 从 store 加载全部偏好。 */
    public static RibbonPreferences load(RibbonPreferenceStore store) {
        return new RibbonPreferences(
            new HashMap<>(store.getAllTabDisplayModes()),
            new ArrayList<>(store.getQatToolIds()),
            store.getSelectedTabId().orElse(null)
        );
    }

    /** 保存全部偏好到 store。 */
    public void save(RibbonPreferenceStore store) {
        for (var entry : tabDisplayModes.entrySet()) {
            store.setTabDisplayMode(entry.getKey(), entry.getValue());
        }
        store.setQatToolIds(new ArrayList<>(qatToolIds));
        if (selectedTabId != null) {
            store.setSelectedTabId(selectedTabId);
        }
    }

    public Optional<TabDisplayMode> getTabDisplayMode(ResourceLocation tabId) {
        return Optional.ofNullable(tabDisplayModes.get(tabId));
    }

    public void setTabDisplayMode(ResourceLocation tabId, TabDisplayMode mode) {
        tabDisplayModes.put(tabId, mode);
    }

    public List<ResourceLocation> getQatToolIds() {
        return Collections.unmodifiableList(qatToolIds);
    }

    public void addQatToolId(ResourceLocation id) {
        if (!qatToolIds.contains(id)) {
            qatToolIds.add(id);
        }
    }

    public void removeQatToolId(ResourceLocation id) {
        qatToolIds.remove(id);
    }

    public Optional<ResourceLocation> getSelectedTabId() {
        return Optional.ofNullable(selectedTabId);
    }

    public void setSelectedTabId(ResourceLocation id) {
        this.selectedTabId = id;
    }
}
