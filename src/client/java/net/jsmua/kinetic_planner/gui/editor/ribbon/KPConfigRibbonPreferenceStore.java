package net.jsmua.kinetic_planner.gui.editor.ribbon;

import net.jsmua.kinetic_planner.config.IKPConfig;
import net.jsmua.kinetic_planner.gui.ribbon.api.RibbonPreferenceStore;
import net.jsmua.kinetic_planner.gui.ribbon.api.TabDisplayMode;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * RibbonPreferenceStore 实现, 读写 IKPConfig (spec §11.5)。
 *
 * <p>桥接框架的 ResourceLocation/TabDisplayMode 与 IKPConfig 的字符串表示。
 *
 * <p><b>getAllTabDisplayModes:</b> IKPConfig 当前未暴露批量查询接口,
 * 首版返回空 Map (RibbonPreferences.load 兜底为空, 不影响运行时)。
 */
public final class KPConfigRibbonPreferenceStore implements RibbonPreferenceStore {

    private final IKPConfig config;

    public KPConfigRibbonPreferenceStore(IKPConfig config) {
        this.config = config;
    }

    @Override
    public Optional<TabDisplayMode> getTabDisplayMode(ResourceLocation tabId) {
        String mode = config.getRibbonTabDisplayMode(toString(tabId));
        if (mode == null) return Optional.empty();
        try {
            return Optional.of(TabDisplayMode.valueOf(mode));
        } catch (IllegalArgumentException e) {
            return Optional.empty();  // 容错: 无效字符串返回 empty
        }
    }

    @Override
    public void setTabDisplayMode(ResourceLocation tabId, TabDisplayMode mode) {
        config.setRibbonTabDisplayMode(toString(tabId), mode.name());
    }

    @Override
    public Map<ResourceLocation, TabDisplayMode> getAllTabDisplayModes() {
        // IKPConfig 当前不暴露批量查询接口, 返回空 Map。
        // 首版 acceptable: RibbonPreferences 初始化时无批量数据, 单次查询由 getTabDisplayMode 处理。
        return Collections.emptyMap();
    }

    @Override
    public List<ResourceLocation> getQatToolIds() {
        var ids = config.getRibbonQatToolIds();
        var result = new ArrayList<ResourceLocation>(ids.size());
        for (var id : ids) {
            var rl = parse(id);
            if (rl != null) result.add(rl);
        }
        return result;
    }

    @Override
    public void setQatToolIds(List<ResourceLocation> ids) {
        var strings = new ArrayList<String>(ids.size());
        for (var id : ids) strings.add(toString(id));
        config.setRibbonQatToolIds(strings);
    }

    @Override
    public Optional<ResourceLocation> getSelectedTabId() {
        var s = config.getRibbonSelectedTab();
        return Optional.ofNullable(s).map(KPConfigRibbonPreferenceStore::parse);
    }

    @Override
    public void setSelectedTabId(ResourceLocation id) {
        config.setRibbonSelectedTab(toString(id));
    }

    private static String toString(ResourceLocation rl) {
        return rl.getNamespace() + ":" + rl.getPath();
    }

    private static ResourceLocation parse(String s) {
        var parts = s.split(":", 2);
        if (parts.length != 2) return null;
        return ResourceLocation.fromNamespaceAndPath(parts[0], parts[1]);
    }
}
