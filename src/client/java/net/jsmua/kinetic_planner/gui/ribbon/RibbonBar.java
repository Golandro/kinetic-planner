package net.jsmua.kinetic_planner.gui.ribbon;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
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
import java.util.Set;

/**
 * Ribbon 栏主容器 (spec §2.1, §10.6)。
 *
 * <p>构造注入 ViewContextProvider + RibbonPreferenceStore, 不调用任何 getInstance()。
 *
 * <p>结构:
 * <pre>
 * RibbonBar (UIElement, COLUMN, width=100%)
 * ├── RibbonHeader (ROW, height=20px): QAT + TabStrip + Trailing components
 * └── RibbonContent: 当前选中 tab 的工具组面板 (PINNED 占布局 / FLOATING 浮层)
 * </pre>
 */
public final class RibbonBar extends UIElement {

    private final ViewContextProvider contextProvider;
    private final RibbonPreferenceStore preferenceStore;
    private final RibbonPreferences preferences;
    private final DefaultQuickAccessToolbar qat;
    private final Map<ResourceLocation, RibbonTabState> tabStates = new HashMap<>();
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

        // 初始化 tab states
        for (var tab : net.jsmua.kinetic_planner.gui.ribbon.registry.RibbonRegistry.getTabsSortedByPriority()) {
            tabStates.put(tab.getId(), new RibbonTabState(tab));
        }
        for (var group : net.jsmua.kinetic_planner.gui.ribbon.registry.RibbonRegistry.getContextualGroups()) {
            for (var tab : group.getTabs()) {
                tabStates.put(tab.getId(), new RibbonTabState(tab));
            }
        }

        // 默认选中
        this.selectedTabId = preferences.getSelectedTabId().orElse(null);

        // 布局
        addClass("kp-ribbon-bar");
        layout(layout -> {
            layout.flexDirection(FlexDirection.COLUMN);
            layout.widthPercent(100);
        });

        // 构建 header + content (委托 RibbonBuilder)
        RibbonBuilder.build(this, tabStates, qat, selectedTabId);
    }

    /** ViewContextProvider 报告变化时调用: 更新 CONTEXTUAL tab 可见性 + 重建 header。 */
    private void onContextChanged() {
        Set<ResourceLocation> active = contextProvider.getActiveContexts();
        for (ContextualTabGroup group : net.jsmua.kinetic_planner.gui.ribbon.registry.RibbonRegistry.getContextualGroups()) {
            boolean isActive = active.contains(group.getId());
            for (RibbonTabDefinition tab : group.getTabs()) {
                var state = tabStates.get(tab.getId());
                if (state != null) {
                    state.setContextActive(isActive);
                }
            }
        }
        rebuildHeader();
    }

    /** 重建 header (清除旧 children, 重新调用 RibbonBuilder.buildHeader)。 */
    void rebuildHeader() {
        RibbonBuilder.rebuildHeader(this, tabStates, qat, selectedTabId);
    }

    /** 用户点击 tab 头时调用: 切换选中 + 重建。 */
    public void selectTab(ResourceLocation id) {
        this.selectedTabId = id;
        this.preferences.setSelectedTabId(id);
        clearAllChildren();
        RibbonBuilder.build(this, tabStates, qat, selectedTabId);
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
