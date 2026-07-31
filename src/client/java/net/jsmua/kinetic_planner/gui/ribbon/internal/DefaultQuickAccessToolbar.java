package net.jsmua.kinetic_planner.gui.ribbon.internal;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import net.jsmua.kinetic_planner.gui.ribbon.QatBar;
import net.jsmua.kinetic_planner.gui.ribbon.api.QuickAccessToolbar;
import net.jsmua.kinetic_planner.gui.ribbon.api.RibbonPreferenceStore;
import net.jsmua.kinetic_planner.gui.ribbon.api.RibbonToolDefinition;
import net.jsmua.kinetic_planner.gui.ribbon.registry.RibbonRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * QuickAccessToolbar 默认实现 (spec §7.1)。
 *
 * <p>状态逻辑 (toolIds 列表) 与 UI 渲染 (QatBar) 分离:
 * 状态操作可单测 (不触发 UIElement <clinit>); createElement() 触发 clinit 留运行时验收。
 */
public final class DefaultQuickAccessToolbar implements QuickAccessToolbar {

    private final List<ResourceLocation> toolIds = new ArrayList<>();
    private final QatBar qatBar;

    public DefaultQuickAccessToolbar(RibbonPreferences preferences) {
        this.toolIds.addAll(preferences.getQatToolIds());
        this.qatBar = new QatBar();
        rebuildBar();
    }

    @Override
    public List<ResourceLocation> getToolIds() {
        return Collections.unmodifiableList(toolIds);
    }

    @Override
    public boolean addTool(ResourceLocation toolId) {
        if (toolIds.contains(toolId)) return false;
        toolIds.add(toolId);
        rebuildBar();
        return true;
    }

    @Override
    public boolean removeTool(ResourceLocation toolId) {
        boolean removed = toolIds.remove(toolId);
        if (removed) rebuildBar();
        return removed;
    }

    @Override
    public boolean containsTool(ResourceLocation toolId) {
        return toolIds.contains(toolId);
    }

    @Override
    public UIElement createElement() {
        return qatBar;
    }

    @Override
    public void loadPreferences(RibbonPreferenceStore store) {
        toolIds.clear();
        toolIds.addAll(store.getQatToolIds());
        rebuildBar();
    }

    @Override
    public void savePreferences(RibbonPreferenceStore store) {
        store.setQatToolIds(new ArrayList<>(toolIds));
    }

    private void rebuildBar() {
        qatBar.rebuild(toolIds, this::lookupTool);
    }

    /** 从 RibbonRegistry 查找 tool definition。 */
    private RibbonToolDefinition lookupTool(ResourceLocation id) {
        for (var tab : RibbonRegistry.getTabsSortedByPriority()) {
            for (var group : tab.getGroups()) {
                for (var tool : group.getTools()) {
                    if (tool.getId().equals(id)) return tool;
                }
            }
        }
        return null;
    }
}
