package net.jsmua.kinetic_planner.gui.ribbon.internal;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TabView;
import net.jsmua.kinetic_planner.gui.ribbon.RibbonBar;
import net.jsmua.kinetic_planner.gui.ribbon.api.QuickAccessToolbar;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link RibbonTabViewAdapter} 签名验证测试 (Task 1)。
 *
 * <p>使用反射验证类/构造器/方法签名, 不触发 UIElement.&lt;clinit&gt; (LDLib2 clinit 陷阱,
 * spec §13)。遵循 {@link RibbonBuilderTest} 与 {@link QatBarTest} 的反射测试模式。
 *
 * <p><b>预期 header 顺序 (Task 1 spec):</b>
 * [LEADING..., tabScroller(flex:1), QAT, TRAILING...]
 * QAT 从 LEADING 最左移至 tabScroller 之后, 实现 "QAT 右对齐" 目标。
 */
class RibbonTabViewAdapterTest {

    private static final String ADAPTER_CLASS_NAME =
        "net.jsmua.kinetic_planner.gui.ribbon.internal.RibbonTabViewAdapter";

    /**
     * 验证 RibbonTabViewAdapter 类存在于 internal 包。
     */
    @Test
    void classExistsInInternalPackage() throws Exception {
        var cls = Class.forName(ADAPTER_CLASS_NAME);
        assertEquals(ADAPTER_CLASS_NAME, cls.getName(),
            "RibbonTabViewAdapter 必须位于 net.jsmua.kinetic_planner.gui.ribbon.internal 包");
        // 不应继承 UIElement (适配器是纯 POJO, 不触发 LDLib2 clinit)
        assertEquals(Object.class, cls.getSuperclass(),
            "RibbonTabViewAdapter 必须直接继承 Object (POJO, 不是 UIElement)");
    }

    /**
     * 验证构造器签名: (Map, QuickAccessToolbar, List, List, Optional)。
     *
     * <p>对应 spec 接口: tabStates / qat / leadingComponents / trailingComponents / preferredTabId。
     */
    @Test
    void constructorSignatureMatchesSpec() throws Exception {
        var cls = Class.forName(ADAPTER_CLASS_NAME);
        var constructor = cls.getDeclaredConstructor(
            Map.class,
            QuickAccessToolbar.class,
            List.class,
            List.class,
            Optional.class);
        assertNotNull(constructor,
            "必须提供 (Map, QuickAccessToolbar, List, List, Optional) 构造器");
    }

    /**
     * 验证 buildTabView(RibbonBar, List&lt;UIElement&gt;) 方法存在且返回 TabView。
     */
    @Test
    void buildTabViewMethodSignatureMatchesSpec() throws Exception {
        var cls = Class.forName(ADAPTER_CLASS_NAME);
        var m = cls.getMethod("buildTabView", RibbonBar.class, java.util.List.class);
        assertEquals(TabView.class, m.getReturnType(),
            "buildTabView 必须返回 TabView");
    }
}
