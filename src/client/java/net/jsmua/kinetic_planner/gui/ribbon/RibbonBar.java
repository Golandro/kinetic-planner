package net.jsmua.kinetic_planner.gui.ribbon;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Tab;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TabView;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.jsmua.kinetic_planner.gui.ribbon.api.ContextualTabGroup;
import net.jsmua.kinetic_planner.gui.ribbon.api.RibbonPreferenceStore;
import net.jsmua.kinetic_planner.gui.ribbon.api.RibbonTabDefinition;
import net.jsmua.kinetic_planner.gui.ribbon.api.RibbonTabState;
import net.jsmua.kinetic_planner.gui.ribbon.api.ViewContextProvider;
import net.jsmua.kinetic_planner.gui.ribbon.internal.DefaultQuickAccessToolbar;
import net.jsmua.kinetic_planner.gui.ribbon.internal.RibbonBuilder;
import net.jsmua.kinetic_planner.gui.ribbon.internal.RibbonPreferences;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Ribbon 栏主容器 (spec §2.1, §10.6)。
 *
 * <p>持有 LDLib2 {@link TabView}, 一次性构建 (委托 {@link RibbonBuilder#build})。
 * Tab 切换不再重建, 由 {@link TabView} 内部管理 content 显隐, 仅触发 {@link #onTabSelected}
 * 持久化 selectedTabId。
 *
 * <p>结构:
 * <pre>
 * RibbonBar (UIElement, COLUMN, kp-ribbon-bar)
 * └── TabView (内置 tabHeaderContainer: LEADING + tabScroller + QAT + TRAILING; tabContentContainer)
 * </pre>
 */
public final class RibbonBar extends UIElement {

    private final ViewContextProvider contextProvider;
    private final RibbonPreferenceStore preferenceStore;
    private final RibbonPreferences preferences;
    private final DefaultQuickAccessToolbar qat;
    private final Map<ResourceLocation, RibbonTabState> tabStates = new HashMap<>();
    private final Map<ResourceLocation, Tab> tabById = new HashMap<>();
    private TabView tabView;
    private ResourceLocation selectedTabId;

    public RibbonBar(ViewContextProvider contextProvider,
                     RibbonPreferenceStore preferenceStore) {
        super();
        this.contextProvider = contextProvider;
        this.preferenceStore = preferenceStore;
        this.preferences = RibbonPreferences.load(preferenceStore);
        this.qat = new DefaultQuickAccessToolbar(preferences);

        // 注册上下文变化监听
        contextProvider.addContextChangeListener(this::onContextChanged);

        // 初始化 tab states; 上下文 tab 根据当前活动上下文同步初始可见性
        var activeContexts = contextProvider.getActiveContexts();
        for (var tab : net.jsmua.kinetic_planner.gui.ribbon.registry.RibbonRegistry.getTabsSortedByPriority()) {
            tabStates.put(tab.getId(), new RibbonTabState(tab));
        }
        for (var group : net.jsmua.kinetic_planner.gui.ribbon.registry.RibbonRegistry.getContextualGroups()) {
            boolean groupActive = activeContexts.contains(group.getId());
            for (var tab : group.getTabs()) {
                var state = new RibbonTabState(tab);
                state.setContextActive(groupActive);
                tabStates.put(tab.getId(), state);
            }
        }

        // 默认选中: 优先持久化偏好, 否则选首个 PINNED tab (确保首屏有内容)
        this.selectedTabId = preferences.getSelectedTabId().orElse(null);
        if (this.selectedTabId == null) {
            var first = net.jsmua.kinetic_planner.gui.ribbon.registry.RibbonRegistry
                .getTabsSortedByPriority().stream().findFirst();
            first.ifPresent(ribbonTabDefinition -> this.selectedTabId = ribbonTabDefinition.getId());
        }

        // 布局
        addClass("kp-ribbon-bar");
        layout(layout -> {
            layout.flexDirection(FlexDirection.COLUMN);
            layout.widthPercent(100);
            layout.flexGrow(1);
        });

        // 一次性构建 (委托 RibbonBuilder)
        RibbonBuilder.build(this, tabStates, qat, Optional.ofNullable(selectedTabId));
    }

    /** RibbonBuilder 回调: 设置 TabView 引用并加入 RibbonBar 子树。 */
    public void setTabView(TabView tabView) {
        this.tabView = tabView;
        // 把 TabView 加为子元素; 首次构造时 children 为空, 直接 add
        if (!getChildren().contains(tabView)) {
            addChild(tabView);
        }
    }

    /** 暴露 TabView, 用于测试 + 运行时检查。 */
    public TabView getTabView() {
        return tabView;
    }

    /** RibbonBuilder 回调: 注册 Tab 与 ID 的映射 (用于 onContextChanged 切换可见性)。 */
    public void registerTab(ResourceLocation id, Tab tab) {
        tabById.put(id, tab);
    }

    /** TabView 选中 tab 时调用: 持久化 selectedTabId。 */
    public void onTabSelected(ResourceLocation id) {
        this.selectedTabId = id;
        this.preferences.setSelectedTabId(id);
    }

    /** ViewContextProvider 报告变化时调用: 调整 contextual tab 的 display, 不重建。 */
    private void onContextChanged() {
        Set<ResourceLocation> active = contextProvider.getActiveContexts();
        for (ContextualTabGroup group : net.jsmua.kinetic_planner.gui.ribbon.registry.RibbonRegistry.getContextualGroups()) {
            boolean isActive = active.contains(group.getId());
            for (RibbonTabDefinition tab : group.getTabs()) {
                var state = tabStates.get(tab.getId());
                if (state != null) {
                    state.setContextActive(isActive);
                }
                var tabEl = tabById.get(tab.getId());
                if (tabEl != null) {
                    tabEl.setDisplay(state != null && state.isHeaderVisible());
                }
            }
        }
    }

    public ViewContextProvider getContextProvider() {
        return contextProvider;
    }

    public RibbonPreferenceStore getPreferenceStore() {
        return preferenceStore;
    }

    public DefaultQuickAccessToolbar getQat() {
        return qat;
    }
}
