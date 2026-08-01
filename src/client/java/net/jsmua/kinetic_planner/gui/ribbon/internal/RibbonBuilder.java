package net.jsmua.kinetic_planner.gui.ribbon.internal;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Tab;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TabView;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.jsmua.kinetic_planner.gui.ribbon.RibbonBar;
import net.jsmua.kinetic_planner.gui.ribbon.api.RibbonHeaderComponent;
import net.jsmua.kinetic_planner.gui.ribbon.api.RibbonTabDefinition;
import net.jsmua.kinetic_planner.gui.ribbon.api.RibbonTabState;
import net.jsmua.kinetic_planner.gui.ribbon.api.TabDisplayMode;
import net.jsmua.kinetic_planner.gui.ribbon.internal.toggle.RibbonToggleGroup;
import net.jsmua.kinetic_planner.gui.ribbon.internal.toggle.ToggleBasedRibbonToggleGroup;
import net.jsmua.kinetic_planner.gui.ribbon.registry.RibbonRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * definition -> TabView UI 树构建器 (spec §2.1)。
 *
 * <p>持有 RibbonBar 的引用, 一次性构建 TabView + header。Tab 切换不再重建, 由 TabView 内部
 * 管理 content 的 display on/off。
 *
 * <p><b>关键设计:</b>
 * <ul>
 *   <li>核心 tab + 上下文 tab 都通过 {@link TabView#addTab(Tab, UIElement)} 加入</li>
 *   <li>contextual tab 的可见性由 {@link Tab#setDisplay(boolean)} 控制, 不需要从 TabView 移除</li>
 *   <li>首次 addTab 会触发 selectTab 回调, 在回调中通过 bar.onTabSelected 持久化选中 ID</li>
 *   <li>header 组装 (LEADING + QAT + TRAILING) 委托 {@link RibbonTabViewAdapter},
 *       产出顺序 [LEADING..., tabScroller(flex:1), QAT, TRAILING...] (QAT 右对齐)</li>
 * </ul>
 */
public final class RibbonBuilder {

    private RibbonBuilder() {}

    /**
     * 首次构建: 委托 {@link RibbonTabViewAdapter} 创建 TabView + 组装 header, 再加入 tabs, 设置给 RibbonBar。
     *
     * @param bar              目标 RibbonBar
     * @param tabStates        所有 tab 的状态 (核心 + 上下文)
     * @param qat              QAT 容器 (用于 header)
     * @param preferredTabId   优先选中的 tab ID (可为 empty)
     */
    public static void build(RibbonBar bar,
                             Map<ResourceLocation, RibbonTabState> tabStates,
                             DefaultQuickAccessToolbar qat,
                             Optional<ResourceLocation> preferredTabId) {
        // === 1. 委托 RibbonTabViewAdapter 构建 TabView + 组装 header ===
        // header 顺序: [LEADING..., tabScroller(flex:1), QAT, TRAILING...] (QAT 右对齐, Task 1)
        var adapter = new RibbonTabViewAdapter(
            tabStates, qat,
            RibbonRegistry.getHeaderComponents(RibbonHeaderComponent.Placement.LEADING),
            RibbonRegistry.getHeaderComponents(RibbonHeaderComponent.Placement.TRAILING),
            preferredTabId);
        var tabView = adapter.buildTabView(bar);
        bar.setTabView(tabView);

        // === 2. 收集所有要显示的 tab (核心 + 上下文 active 的) ===
        // 注意: 上下文 tab 即使未 active, 也加入 TabView 但 setDisplay(false), 方便切换时显示
        var allTabs = new ArrayList<RibbonTabDefinition>();
        for (var tab : RibbonRegistry.getTabsSortedByPriority()) {
            allTabs.add(tab);
        }
        for (var group : RibbonRegistry.getContextualGroups()) {
            allTabs.addAll(group.getTabs());
        }

        // === 3. 为每个 tab 创建 Tab + content, 加入 TabView ===
        Tab preferredTab = null;
        for (var tabDef : allTabs) {
            var state = tabStates.get(tabDef.getId());
            if (state == null) continue;

            var tab = new Tab();
            tab.setText(tabDef.getDisplayName());
            tab.addClass("kp-ribbon-tab");
            tab.setId(tabDef.getId().toString());

            // content: ROW 排列所有 groups, 高度由 flex 填充父容器
            var content = new UIElement();
            content.addClass("kp-ribbon-content");
            content.layout(layout -> {
                layout.flexDirection(FlexDirection.ROW);
                layout.flexGrow(1);
                layout.widthPercent(100);
            });

            // per-tab mutex 组映射：同 tab 内相同 mutex id 的 group 共享一个 RibbonToggleGroup，跨 tab 隔离
            Map<ResourceLocation, RibbonToggleGroup> mutexGroups = new HashMap<>();

            for (var group : tabDef.getGroups()) {
                RibbonToggleGroup mutexGroup = null;
                var mutexId = group.getMutualExclusionGroupId();
                if (mutexId.isPresent()) {
                    mutexGroup = mutexGroups.computeIfAbsent(
                        mutexId.get(),
                        k -> new ToggleBasedRibbonToggleGroup()
                    );
                }
                content.addChild(GroupPanel.build(group, mutexGroup));
            }

            // 上下文 tab: 初始 display 由 contextActive 决定
            if (state.getDisplayMode() == TabDisplayMode.CONTEXTUAL) {
                tab.setDisplay(state.isHeaderVisible());
            } else if (!state.isHeaderVisible()) {
                // HIDDEN 等不可见模式
                tab.setDisplay(false);
            }

            tabView.addTab(tab, content);
            bar.registerTab(tabDef.getId(), tab);

            if (preferredTabId.isPresent() && preferredTabId.get().equals(tabDef.getId())) {
                preferredTab = tab;
            }
        }

        // === 4. 设置 tab 选中回调 (持久化 selectedTabId) ===
        // 注意: 首次 addTab 已触发一次 selectTab 回调, 此时 onTabSelected 还未设置, 安全。
        tabView.setOnTabSelected(selectedTab -> {
            var id = tabIdOf(selectedTab);
            if (id != null) {
                bar.onTabSelected(id);
            }
        });

        // === 5. 选中 preferredTab (如果未触发过自动选中) ===
        if (preferredTab != null && tabView.getSelectedTab() != preferredTab) {
            tabView.selectTab(preferredTab);
        }
        // 若 preferredTab 为 null 且 selectedTab 也为 null (空 TabView), 不做处理
    }

    /** 从 Tab.id (ResourceLocation.toString()) 反解析 ResourceLocation。 */
    private static ResourceLocation tabIdOf(Tab tab) {
        if (tab == null) return null;
        var id = tab.getId();
        if (id == null || id.isEmpty()) return null;
        return ResourceLocation.tryParse(id);
    }
}
