package net.jsmua.kinetic_planner.gui.editor;

import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import com.lowdragmc.lowdraglib2.editor.ui.EditorLayoutStore;
import com.lowdragmc.lowdraglib2.gui.texture.Icons;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.util.TreeBuilder;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.jsmua.kinetic_planner.KineticPlannerClient;
import net.jsmua.kinetic_planner.config.IKPConfig;
import net.jsmua.kinetic_planner.gui.MapPlaceholderView;
import net.jsmua.kinetic_planner.gui.ToolPanelView;
import net.jsmua.kinetic_planner.gui.editor.ribbon.KPConfigRibbonPreferenceStore;
import net.jsmua.kinetic_planner.gui.editor.ribbon.KpViewContextProvider;
import net.jsmua.kinetic_planner.gui.ribbon.RibbonBar;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LDLib2 {@link Editor} 子类 (spec §6.3)。
 *
 * <p>使用通用 RibbonBar (gui/ribbon/) 替代旧 KpRibbonBar。
 * RibbonBar 通过构造注入 ViewContextProvider + PreferenceStore, 不调 getInstance()。
 *
 * <p><b>上下文状态:</b> 当前编辑器实例持有 {@link #activeContexts} 集合,
 * 通过 {@link KpViewContextProvider} 注入的 supplier 报告给 RibbonBar。
 * View tab 的 "Ctx Tab" 演示开关通过运行时查找当前编辑器实例来切换此状态。
 */
public class KpMapEditor extends Editor {

    /** 演示上下文 ID (与 KpRibbonRegistration 中注册的 contextual group ID 对应)。 */
    public static final ResourceLocation DEMO_CONTEXT_ID =
        ResourceLocation.fromNamespaceAndPath("kp", "demo_context_group");

    /** 固定 project type 名称, 用于 EditorLayoutStore 保存/恢复编辑器窗格布局。 */
    private static final String PROJECT_TYPE_NAME = "kinetic_planner_editor";

    @Nullable
    private MapPlaceholderView mapViewport;

    @Nullable
    private KpViewContextProvider kpViewContextProvider;

    /** 当前编辑会话的活动上下文集合; 由 View tab 的 "Ctx Tab" 开关修改。 */
    private final Set<ResourceLocation> activeContexts = ConcurrentHashMap.newKeySet();

    public KpMapEditor() {
        super();
    }

    @Nullable
    public MapPlaceholderView getMapViewport() {
        return mapViewport;
    }

    @Nullable
    public KpViewContextProvider getKpViewContextProvider() {
        return kpViewContextProvider;
    }

    /**
     * 返回当前活动上下文的不可修改视图 (供 KpViewContextProvider supplier 使用)。
     *
     * <p><b>null 防御:</b> LDLib2 {@link Editor} 构造器在 {@code super()} 内部调用
     * {@link #initMenus()}, 此时 Java 实例字段初始化器尚未执行, {@code activeContexts}
     * 仍为 {@code null}。构造期间无上下文激活, 返回空集合语义正确; 构造完成后字段
     * 初始化器执行, 后续访问永远非 {@code null}。
     */
    public Set<ResourceLocation> getActiveContexts() {
        return activeContexts != null
            ? Collections.unmodifiableSet(activeContexts)
            : Collections.emptySet();
    }

    /** 演示上下文是否激活。 */
    public boolean isDemoContextActive() {
        return activeContexts.contains(DEMO_CONTEXT_ID);
    }

    /** 切换演示上下文激活状态; 修改后通知 {@link KpViewContextProvider} 触发 RibbonBar 调整 contextual tab 可见性。 */
    public void setDemoContextActive(boolean active) {
        boolean changed;
        if (active) {
            changed = activeContexts.add(DEMO_CONTEXT_ID);
        } else {
            changed = activeContexts.remove(DEMO_CONTEXT_ID);
        }
        if (changed && kpViewContextProvider != null) {
            kpViewContextProvider.notifyContextChanged();
        }
    }

    @Override
    protected Editor createNewEditorInstance() {
        return new KpMapEditor();
    }

    @Override
    protected void initMenus() {
        // 不调用 super.initMenus() -- 不添加 FileMenu/ViewMenu

        // 构造 KP 适配层的 ViewContextProvider + PreferenceStore
        this.kpViewContextProvider = new KpViewContextProvider(this::getActiveContexts);
        var preferenceStore = new KPConfigRibbonPreferenceStore(getKpConfig());

        // Ribbon 栏独占整个 Editor top 区域，替代默认标题栏。
        // 默认 top 包含 icon/menuContainer/topPlaceholder/buttonContainer，这里全部清除。
        top.clearAllChildren();
        top.getLayout().flexDirection(FlexDirection.COLUMN);
        top.getLayout().widthPercent(100);
        top.getLayout().heightAuto();

        RibbonBar ribbonBar = new RibbonBar(kpViewContextProvider, preferenceStore, createRightHeaderWidgets());
        top.addChild(ribbonBar);
    }

    /**
     * 创建 Ribbon header 右侧控件：从右到左依次为关闭、帮助、下拉菜单、快速工具栏。
     * 在 header 中按传入顺序追加，因此最终视觉顺序（从左到右）为 QAT -> 下拉 -> 帮助 -> 关闭。
     */
    private List<UIElement> createRightHeaderWidgets() {
        var rightWidgets = new ArrayList<UIElement>();

        // 1) 快速工具配置菜单（下拉入口，包含多余快速工具 + Ribbon 显示控制）
        var overflowButton = new Button();
        overflowButton.setText("▼");
        overflowButton.addClass("kp-ribbon-header-button");
        overflowButton.layout(layout -> layout.heightPercent(100));
        overflowButton.setOnClick(event -> {
            var menu = TreeBuilder.Menu.start()
                .leaf("Quick Access Tools", () -> {
                    // TODO: 打开 QAT 配置面板
                })
                .crossLine()
                .leaf("Ribbon Display Options", () -> {
                    // TODO: 打开 Ribbon 显示控制面板
                })
                .crossLine()
                .leaf("Toggle Demo Context", () -> setDemoContextActive(!isDemoContextActive()));
            openMenu(event.currentElement.getPositionX(),
                     event.currentElement.getPositionY() + event.currentElement.getSizeHeight(),
                     menu);
        });
        rightWidgets.add(overflowButton);

        // 2) 帮助按钮
        var helpButton = new Button();
        helpButton.setText("?");
        helpButton.addClass("kp-ribbon-header-button");
        helpButton.layout(layout -> layout.heightPercent(100));
        helpButton.setOnClick(event -> {
            // TODO: 打开帮助文档/对话框
        });
        rightWidgets.add(helpButton);

        // 3) 关闭按钮（替代 Editor 默认 closeButton，直接关闭编辑器）
        var closeButton = new Button();
        closeButton.noText();
        closeButton.addPreIcon(Icons.WINDOW_CLOSE);
        closeButton.addClass("kp-ribbon-header-button");
        closeButton.addClass("__white_icon__");
        closeButton.layout(layout -> layout.heightPercent(100));
        closeButton.setOnClick(event -> close());
        rightWidgets.add(closeButton);

        // 统一让右侧按钮垂直居中
        for (var widget : rightWidgets) {
            widget.layout(layout -> layout.alignItems(AlignItems.CENTER));
        }
        return rightWidgets;
    }

    /**
     * 获取 KP 配置 (运行时由 KineticPlannerClient 注入)。
     * 通过全局 CONFIG 引用获取, 避免在 initMenus 中调 getInstance()。
     */
    protected IKPConfig getKpConfig() {
        return KineticPlannerClient.CONFIG;
    }

    @Override
    protected void onPrepareResourceView() {
        // 空实现: 不启用 ResourceView
    }

    @Override
    protected void onPrepareHistoryView() {
        // 空实现: 不放置 HistoryView 在 UI 中
        // HistoryView 对象仍存在（public final 字段），InspectorView 引用其作为 historyStack
        // undo/redo 由后端 VCS 系统处理（P4 阶段）
    }

    /**
     * 放置自定义 View 到各 window, 并恢复上次保存的窗格布局。
     *
     * <p>必须在 {@link Editor} 构造完成（{@code rootWindow}/{@code leftWindow}/
     * {@code centerWindow}/{@code rightWindow} 已初始化）后调用。
     *
     * <p>流程:
     * <ol>
     *   <li>先创建默认 View 并放置到默认位置, 使 {@link Editor#applyLayout} 能收集到它们。</li>
     *   <li>尝试从 {@link EditorLayoutStore} 加载上次保存的布局; 存在则调用 {@code applyLayout}
     *       恢复窗格树与 View 位置, <b>不再压平</b> 布局。</li>
     *   <li>无保存布局时, 按默认行为压平为 left+center 两栏。</li>
     *   <li>隐藏主/侧面板的 collapse 按钮, 清空 center 背景链。</li>
     * </ol>
     */
    public void placeCustomViews() {
        // 1. 创建默认 View 并先放到默认位置, 确保 applyLayout 能收集到 live views。
        var toolPanel = new ToolPanelView();
        var mapViewport = new MapPlaceholderView();
        this.mapViewport = mapViewport;
        placeView(toolPanel, () -> leftWindow.getRightTop());
        placeView(mapViewport, () -> centerWindow.getRightTop());

        // 2. 若存在保存的布局则恢复, 否则保持默认四栏树等待下一步压平。
        var savedLayout = EditorLayoutStore.load(PROJECT_TYPE_NAME);
        savedLayout.ifPresent(this::applyLayout);

        // 3. 首次打开 (无保存布局) 时压平为 left+center。
        if (savedLayout.isEmpty()) {
            flattenMainAreaLayout();
        }

        // 4. 隐藏 collapse 按钮; 清空 center 背景链。
        hideCollapseButtons();
        MapPlaceholderView.prepareTransparentChain(centerWindow.getViewContainer());
    }

    /**
     * 隐藏左/中面板的 collapse 按钮, 防止用户误收缩主工作区。
     */
    private void hideCollapseButtons() {
        if (leftWindow != null && leftWindow.getViewContainer() != null) {
            leftWindow.getViewContainer().collapseButton.setDisplay(false);
        }
        if (centerWindow != null && centerWindow.getViewContainer() != null) {
            centerWindow.getViewContainer().collapseButton.setDisplay(false);
        }
    }

    /**
     * 保存当前编辑器窗格布局到 {@link EditorLayoutStore}。
     *
     * <p>由 {@link KpEditorScreen#onClose()} 调用, 在退出编辑模式时持久化用户拖拽后的布局。
     */
    public void saveEditorLayout() {
        EditorLayoutStore.save(PROJECT_TYPE_NAME, captureLayout());
    }

    /**
     * 将 LDLib2 Editor 默认四窗格树扁平为仅 leftWindow + centerWindow, 降低 UIElement DOM 深度。
     *
     * <p>Editor 构造器构建的默认树:
     * <pre>
     * rootWindow (水平 80%)
     * ├── split1.first (垂直 75%)
     * │   ├── split2.first (水平 28%)
     * │   │   ├── leftWindow
     * │   │   └── centerWindow
     * │   └── bottomWindow
     * └── rightWindow
     * </pre>
     *
     * <p>{@code setImmortal}/{@code setAnchorId} 为 protected (跨包不可调), 故无法重建树。
     * 改用公共 {@code SplittableWindow.removeSplitWindow} 逐个移除 rightWindow / bottomWindow:
     * 因 rootWindow 为 immortal + anchored, 移除触发 {@code replaceContentWith}, 将幸存兄弟节点
     * 的 split 提升到 rootWindow。两次移除后 rootWindow 直接承载 leftWindow/centerWindow
     * 水平分割 (~28/72)。
     *
     * <p>rightWindow/bottomWindow 字段引用保持不变 (仍指向已脱离树的窗口对象);
     * {@link Editor#captureLayout()}/{@link Editor#applyLayout} 仅遍历 rootWindow 活子树
     * 并通过 anchor 注册表重绑, 可容忍此状态。inspectorView (默认置于 rightWindow) 随之隐藏,
     * 符合"仅左+中可见"目标。运行时行为靠 {@code gradlew runClient} 验收 (clinit 限制无法单测)。
     */
    private void flattenMainAreaLayout() {
        // 1. 移除 rightWindow: rootWindow 水平分割坍缩为垂直分割 (split1.first),
        //    bottomWindow 提升为 rootWindow.second。
        rootWindow.removeSplitWindow(rightWindow);
        // 2. 移除 bottomWindow: rootWindow 垂直分割坍缩为水平分割 (split2.first),
        //    leftWindow/centerWindow 直接挂到 rootWindow 下。
        rootWindow.removeSplitWindow(bottomWindow);
        // 3. 同步 split style: leftWindow ~28% / centerWindow ~72%。
        rootWindow.splitStyle(style -> style.percentage(28).minPercentage(5).maxPercentage(95));
    }
}
