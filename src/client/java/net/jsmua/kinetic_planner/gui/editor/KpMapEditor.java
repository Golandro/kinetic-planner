package net.jsmua.kinetic_planner.gui.editor;

import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import net.jsmua.kinetic_planner.gui.KpRibbonBar;
import net.jsmua.kinetic_planner.gui.MapPlaceholderView;
import net.jsmua.kinetic_planner.gui.ToolPanelView;
import org.jetbrains.annotations.Nullable;

/**
 * LDLib2 {@link Editor} 子类（spec §6.3）。
 *
 * <p>精简配置：不启用 FileMenu/ViewMenu（替换为 KpRibbonBar 在 Phase 4），
 * 不启用 ResourceView/HistoryView，启用 InspectorView。
 *
 * <p>中心区域彻底掏空：不放置任何 View，将 {@code centerWindow} 的 {@code ViewContainer}
 * 背景置为 {@link IGuiTexture#EMPTY} 并禁用命中测试，让全屏渲染的地图/CAD 层直接透出并接收事件。
 * 左侧放置 {@link ToolPanelView}（Phase 4 实现），
 * 右侧由 {@code super.onPrepareInspectorView()} 默认放置 InspectorView。
 *
 * <p>closeButton 链路：{@code currentProject} 始终为 null，
 * {@code askToSaveProject} 跳过对话框，{@code exit} 调用
 * {@code ModularUI.getScreen().onClose()}（spec §6.3 已验证）。
 */
public class KpMapEditor extends Editor {

    @Nullable
    private MapPlaceholderView mapViewport;

    public KpMapEditor() {
        super();
        // Editor 构造函数会调用 initMenus + onPrepareXxx + 创建 rootWindow/leftWindow 等
    }

    /**
     * 获取中心主视口占位 View，用于限制地图导航/绘图事件范围。
     *
     * @return 中心 {@link MapPlaceholderView}，若尚未放置则返回 {@code null}
     */
    @Nullable
    public MapPlaceholderView getMapViewport() {
        return mapViewport;
    }

    @Override
    protected Editor createNewEditorInstance() {
        return new KpMapEditor();
    }

    /**
     * 替换默认 FileMenu/ViewMenu 为空容器（Phase 4 由 KpRibbonBar 填充）。
     *
     * <p>spec §6.3 陷阱：只清除 {@code menuContainer}，保留 {@code buttonContainer}
     * + {@code closeButton} 重定向链路。若误调 {@code top.clearAllChildren()} 会移除
     * {@code buttonContainer}（含 {@code closeButton}）。
     */
    @Override
    protected void initMenus() {
        // 不调用 super.initMenus() -- 不添加 FileMenu/ViewMenu
        menuContainer.clearAllChildren();
        // 替换为 KpRibbonBar
        menuContainer.addChild(new KpRibbonBar());
        // 调整 top 高度（Ribbon 需 ~24px）
        top.getLayout().height(24);
    }

    @Override
    protected void onPrepareResourceView() {
        // 空实现：不启用 ResourceView
    }

    @Override
    protected void onPrepareHistoryView() {
        // 空实现：不放置 HistoryView 在 UI 中
        // HistoryView 对象仍存在（public final 字段），InspectorView 引用其作为 historyStack
        // undo/redo 由后端 VCS 系统处理（P4 阶段）
    }

    /**
     * 放置自定义 View 到各 window。
     *
     * <p>必须在 {@link Editor} 构造完成（{@code rootWindow}/{@code leftWindow}/
     * {@code centerWindow}/{@code rightWindow} 已初始化）后调用。
     * 使用 {@link #placeView(View, java.util.function.Supplier)} 的 fallback 分支
     * （{@code savedLayout == null} 时）。
     *
     * <p>调用时机：由 {@code KpEditorScreen} 在构造后调用一次。
     */
    public void placeCustomViews() {
        // 左侧：ToolPanelView（Phase 4 创建，此处先放占位）
        placeView(new ToolPanelView(), () -> leftWindow.getRightTop());
        // 中心：放置透明占位 View，保留 ViewContainer 的 Tab 栏以便未来扩展。
        // ViewContainer 默认背景是 Sprites.RECT_SOLID（灰色实心），必须显式置空；
        // MapPlaceholderView 自身也显式置空，负责限制主视口事件范围。
        this.mapViewport = new MapPlaceholderView();
        placeView(this.mapViewport, () -> centerWindow.getRightTop());
        centerWindow.getViewContainer().getStyle().backgroundTexture(IGuiTexture.EMPTY);
        // 右侧：InspectorView 已由 onPrepareInspectorView 默认放置，无需重复
    }
}
