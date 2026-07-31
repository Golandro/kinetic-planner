package net.jsmua.kinetic_planner.gui.ribbon.internal;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.jsmua.kinetic_planner.gui.ribbon.RibbonBar;
import net.jsmua.kinetic_planner.gui.ribbon.api.RibbonHeaderComponent;
import net.jsmua.kinetic_planner.gui.ribbon.api.RibbonTabDefinition;
import net.jsmua.kinetic_planner.gui.ribbon.api.RibbonTabState;
import net.jsmua.kinetic_planner.gui.ribbon.registry.RibbonRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/**
 * definition -> UIElement 树构建器 (spec §2.1)。
 *
 * <p>所有 UI 构造集中于此, 便于主题切换时整体重建。
 */
public final class RibbonBuilder {

    private RibbonBuilder() {}

    /** 首次构建: header + content。 */
    public static void build(RibbonBar bar,
                              Map<ResourceLocation, RibbonTabState> tabStates,
                              DefaultQuickAccessToolbar qat,
                              ResourceLocation selectedTabId) {
        buildHeader(bar, tabStates, qat, selectedTabId);
        buildContent(bar, tabStates, selectedTabId);
    }

    /** 仅重建 header (CONTEXTUAL 激活/停用时调用)。 */
    public static void rebuildHeader(RibbonBar bar,
                                      Map<ResourceLocation, RibbonTabState> tabStates,
                                      DefaultQuickAccessToolbar qat,
                                      ResourceLocation selectedTabId) {
        // 简化实现: 清除全部 children 再重建 (运行时优化留 Phase 2+)
        bar.clearAllChildren();
        buildHeader(bar, tabStates, qat, selectedTabId);
        buildContent(bar, tabStates, selectedTabId);
    }

    private static void buildHeader(RibbonBar bar,
                                     Map<ResourceLocation, RibbonTabState> tabStates,
                                     DefaultQuickAccessToolbar qat,
                                     ResourceLocation selectedTabId) {
        var header = new UIElement();
        header.addClass("kp-ribbon-header");
        header.layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.height(20);
        });

        // LEADING: QAT (最左) + LEADING components
        header.addChild(qat.createElement());
        for (var comp : RibbonRegistry.getHeaderComponents(RibbonHeaderComponent.Placement.LEADING)) {
            header.addChild(comp.createElement());
        }

        // TabStrip (flex=1, 可横向滚动 - 简化: 直接 ROW)
        var tabStrip = new UIElement();
        tabStrip.addClass("kp-ribbon-tab-strip");
        tabStrip.layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.flexGrow(1);
        });
        for (var tab : RibbonRegistry.getTabsSortedByPriority()) {
            var state = tabStates.get(tab.getId());
            if (state == null || !state.isHeaderVisible()) continue;
            var tabBtn = new Button();
            tabBtn.setText(tab.getDisplayName());
            tabBtn.setOnClick(event -> bar.selectTab(tab.getId()));
            tabStrip.addChild(tabBtn);
        }
        // 上下文 tabs
        for (var group : RibbonRegistry.getContextualGroups()) {
            for (var tab : group.getTabs()) {
                var state = tabStates.get(tab.getId());
                if (state == null || !state.isHeaderVisible()) continue;
                var tabBtn = new Button();
                tabBtn.setText(tab.getDisplayName());
                tabBtn.setOnClick(event -> bar.selectTab(tab.getId()));
                tabStrip.addChild(tabBtn);
            }
        }
        header.addChild(tabStrip);

        // TRAILING components
        for (var comp : RibbonRegistry.getHeaderComponents(RibbonHeaderComponent.Placement.TRAILING)) {
            header.addChild(comp.createElement());
        }

        bar.addChild(header);
    }

    private static void buildContent(RibbonBar bar,
                                      Map<ResourceLocation, RibbonTabState> tabStates,
                                      ResourceLocation selectedTabId) {
        if (selectedTabId == null) return;
        var state = tabStates.get(selectedTabId);
        if (state == null || !state.getContentVisible()) return;

        var content = new UIElement();
        content.addClass("kp-ribbon-content");
        content.layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.height(40);
        });

        for (var group : state.getDefinition().getGroups()) {
            content.addChild(GroupPanel.build(group));
        }

        bar.addChild(content);
    }
}
