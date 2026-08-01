package net.jsmua.kinetic_planner.gui.ribbon.internal;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TabView;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.jsmua.kinetic_planner.gui.ribbon.RibbonBar;
import net.jsmua.kinetic_planner.gui.ribbon.api.QuickAccessToolbar;
import net.jsmua.kinetic_planner.gui.ribbon.api.RibbonHeaderComponent;
import net.jsmua.kinetic_planner.gui.ribbon.api.RibbonTabState;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * TabView 布局封装 (spec §2.1, Task 1)。
 *
 * <p>将 {@link RibbonBuilder} 中的 header 组装逻辑提取为独立适配器, 产出 header 顺序:
 * <pre>[LEADING..., tabScroller(flex:1), QAT, TRAILING...]</pre>
 *
 * <p><b>QAT 右对齐:</b> QAT 从原 LEADING 最左侧移至 tabScroller 之后, 与 TRAILING 组件
 * 一起位于右侧。LEADING 组件保留在 tabScroller 之前 (左侧)。
 *
 * <p><b>addChildAt vs addChild (Task 1 Correction 1):</b>
 * {@link TabView} 构造器已把 {@code tabScroller} 加入 {@code tabHeaderContainer}。LEADING
 * 组件必须用 {@code addChildAt(elem, index)} 插入到 tabScroller 之前 (索引 0..n-1),
 * 不能用 {@code addChild} (追加到 tabScroller 之后)。QAT 与 TRAILING 追加到 tabScroller 之后。
 *
 * <p>本类是纯 POJO (不继承 UIElement), 构造不触发 LDLib2 clinit; 仅 {@link #buildTabView}
 * 在运行时创建 TabView 实例 (由 RibbonBuilder 在 runClient 中调用)。
 *
 * <p><b>消费者:</b> {@link RibbonBuilder#build} 委托本类完成 header 组装。
 */
public final class RibbonTabViewAdapter {

    private final Map<ResourceLocation, RibbonTabState> tabStates;
    private final QuickAccessToolbar qat;
    private final List<RibbonHeaderComponent> leadingComponents;
    private final List<RibbonHeaderComponent> trailingComponents;
    private final Optional<ResourceLocation> preferredTabId;

    /**
     * @param tabStates          所有 tab 的状态 (核心 + 上下文), 保留供后续扩展使用
     * @param qat                QAT 容器, 提供 {@link QuickAccessToolbar#createElement()}
     * @param leadingComponents  LEADING 侧 header 组件 (已按 priority 升序)
     * @param trailingComponents TRAILING 侧 header 组件 (已按 priority 升序)
     * @param preferredTabId     优先选中的 tab ID (可为 empty)
     */
    public RibbonTabViewAdapter(Map<ResourceLocation, RibbonTabState> tabStates,
                                 QuickAccessToolbar qat,
                                 List<RibbonHeaderComponent> leadingComponents,
                                 List<RibbonHeaderComponent> trailingComponents,
                                 Optional<ResourceLocation> preferredTabId) {
        this.tabStates = tabStates;
        this.qat = qat;
        this.leadingComponents = leadingComponents;
        this.trailingComponents = trailingComponents;
        this.preferredTabId = preferredTabId;
    }

    /**
     * 构建并配置 TabView, 组装 header 顺序:
     * {@code [LEADING..., tabScroller(flex:1), QAT, rightHeaderWidgets..., TRAILING...]}。
     *
     * <p>步骤:
     * <ol>
     *   <li>创建 TabView, 配置自身布局 (widthPercent:100, flexGrow:1)</li>
     *   <li>配置 tabHeaderContainer (ROW, alignItems:CENTER, widthPercent:100)</li>
     *   <li>配置 tabScroller (flexGrow:1, flexShrink:1) 占据中间剩余空间</li>
     *   <li>用 addChildAt 把 LEADING 组件插入 tabScroller 之前 (索引 0..n-1)</li>
     *   <li>用 addChild 把 QAT 追加到 tabScroller 之后</li>
     *   <li>用 addChild 把 rightHeaderWidgets 追加到 QAT 之后 (关闭/帮助/下拉菜单等)</li>
     *   <li>用 addChild 把 TRAILING 组件追加到 rightHeaderWidgets 之后</li>
     *   <li>配置 tabContentContainer (flexGrow:1) 占据下方剩余空间</li>
     * </ol>
     *
     * @param owner            目标 RibbonBar (保留以匹配 spec 接口, 当前未使用)
     * @param rightHeaderWidgets 编辑器传入的右侧控件 (可为空)
     * @return 配置好的 TabView (header 已组装, content 容器已布局; tabs 尚未填充)
     */
    public TabView buildTabView(RibbonBar owner, List<UIElement> rightHeaderWidgets) {
        var tabView = new TabView();

        // TabView 自身: 撑满父容器
        tabView.layout(layout -> {
            layout.widthPercent(100);
            layout.flexGrow(1);
        });

        // tabHeaderContainer: ROW 排列, 垂直居中, 100% 宽度
        // (TabView 构造器已设 flexDirection:ROW + paddingHorizontal:3 + widthPercent:100,
        //  这里补充 alignItems:CENTER, 覆盖不会丢失已有 padding)
        var header = tabView.tabHeaderContainer;
        header.layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.widthPercent(100);
        });

        // tabScroller: 占据中间剩余空间 (TabView 构造器已设 widthPercent:100 + marginBottom:-2,
        //  这里改为 flexGrow:1 让它与 QAT/右侧组件共享横向空间)
        tabView.tabScroller.layout(layout -> {
            layout.flexGrow(1);
            layout.flexShrink(1);
        });

        // === 组装 header: [LEADING..., tabScroller, QAT, rightHeaderWidgets..., TRAILING...] ===
        // tabHeaderContainer 当前结构: [tabScroller]; LEADING 用 addChildAt 插到 tabScroller 前
        for (int i = 0; i < leadingComponents.size(); i++) {
            header.addChildAt(leadingComponents.get(i).createElement(), i);
        }
        // QAT 追加到 tabScroller 之后 (Task 1: QAT 右对齐)
        header.addChild(qat.createElement());
        // 编辑器传入的右侧控件追加到 QAT 之后
        for (var widget : rightHeaderWidgets) {
            header.addChild(widget);
        }
        // TRAILING 追加到最右侧
        for (var comp : trailingComponents) {
            header.addChild(comp.createElement());
        }

        // tabContentContainer: 占据下方剩余空间 (TabView 构造器已设 flexGrow:1 + paddingAll:5)
        tabView.tabContentContainer.layout(layout -> {
            layout.flexGrow(1);
        });

        return tabView;
    }
}
