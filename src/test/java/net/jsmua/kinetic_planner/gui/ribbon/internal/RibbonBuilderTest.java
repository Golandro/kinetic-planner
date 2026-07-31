package net.jsmua.kinetic_planner.gui.ribbon.internal;

import com.lowdragmc.lowdraglib2.gui.ui.elements.TabView;
import net.jsmua.kinetic_planner.gui.ribbon.RibbonBar;
import net.jsmua.kinetic_planner.gui.ribbon.api.RibbonPreferenceStore;
import net.jsmua.kinetic_planner.gui.ribbon.api.RibbonTabDefinition;
import net.jsmua.kinetic_planner.gui.ribbon.api.SimpleRibbonTabDefinition;
import net.jsmua.kinetic_planner.gui.ribbon.api.TabDisplayMode;
import net.jsmua.kinetic_planner.gui.ribbon.api.ViewContextProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link RibbonBuilder} 行为测试。
 *
 * <p><b>测试环境限制:</b> 触发 UIElement.<clinit> → LDLib2 clinit 陷阱。
 * 运行时用例 {@code @Disabled}, 靠 gradlew runClient 验收。
 */
class RibbonBuilderTest {

    /** 空 provider, 用于避免空指针。 */
    private static ViewContextProvider emptyProvider() {
        return ViewContextProvider.empty();
    }

    /** 空 preference store, 用于避免空指针。 */
    private static RibbonPreferenceStore emptyStore() {
        return new RibbonPreferenceStore() {
            @Override public Optional<net.jsmua.kinetic_planner.gui.ribbon.api.TabDisplayMode> getTabDisplayMode(ResourceLocation tabId) { return Optional.empty(); }
            @Override public void setTabDisplayMode(ResourceLocation tabId, net.jsmua.kinetic_planner.gui.ribbon.api.TabDisplayMode mode) {}
            @Override public java.util.Map<ResourceLocation, net.jsmua.kinetic_planner.gui.ribbon.api.TabDisplayMode> getAllTabDisplayModes() { return java.util.Collections.emptyMap(); }
            @Override public List<ResourceLocation> getQatToolIds() { return java.util.Collections.emptyList(); }
            @Override public void setQatToolIds(List<ResourceLocation> ids) {}
            @Override public Optional<ResourceLocation> getSelectedTabId() { return Optional.empty(); }
            @Override public void setSelectedTabId(ResourceLocation id) {}
        };
    }

    private static RibbonTabDefinition makeTab(String path, int priority, TabDisplayMode mode) {
        return new SimpleRibbonTabDefinition(
            ResourceLocation.fromNamespaceAndPath("kp", path),
            Component.literal(path),
            Optional.empty(),
            priority,
            List.of(),
            mode,
            true,
            Optional.empty()
        );
    }

    /**
     * 验证 RibbonBar 构造后内部 TabView 包含核心 tab (运行时验收)。
     */
    @Test
    @Disabled("UIElement.<clinit> 需要 NeoForge 运行时; 靠 gradlew runClient 验收")
    void buildCreatesTabViewWithTabs() {
        // 测试需通过 RibbonRegistry 注册 tab, 此处仅占位说明意图:
        // 构造 RibbonBar -> 取内部 TabView -> 验证 getTabContents() 大于 0
        // 完整运行时验证由 gradlew runClient 完成
        var bar = new RibbonBar(emptyProvider(), emptyStore());
        var tabView = bar.getTabView();
        assertNotNull(tabView);
        assertFalse(tabView.getTabContents().isEmpty(), "TabView 必须至少包含 1 个 tab");
    }

    /**
     * 验证 RibbonBar 持有 TabView getter (不触发 clinit)。
     */
    @Test
    void ribbonBarExposesTabViewGetter() throws NoSuchMethodException {
        var m = RibbonBar.class.getMethod("getTabView");
        assertEquals(TabView.class, m.getReturnType(),
            "RibbonBar.getTabView 必须返回 TabView");
    }

    /**
     * 验证 RibbonBuilder.build 方法签名匹配 Task 4 spec。
     */
    @Test
    void buildMethodSignatureMatchesSpec() throws NoSuchMethodException {
        var m = RibbonBuilder.class.getMethod("build",
            RibbonBar.class,
            java.util.Map.class,
            DefaultQuickAccessToolbar.class,
            java.util.Optional.class);
        assertEquals(void.class, m.getReturnType(),
            "build 必须返回 void");
    }
}
