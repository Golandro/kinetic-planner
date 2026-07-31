package net.jsmua.kinetic_planner.gui.editor;

import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import net.jsmua.kinetic_planner.KineticPlannerClient;
import net.jsmua.kinetic_planner.config.IKPConfig;
import net.jsmua.kinetic_planner.gui.MapPlaceholderView;
import net.jsmua.kinetic_planner.gui.ToolPanelView;
import net.jsmua.kinetic_planner.gui.editor.ribbon.KPConfigRibbonPreferenceStore;
import net.jsmua.kinetic_planner.gui.editor.ribbon.KpViewContextProvider;
import net.jsmua.kinetic_planner.gui.ribbon.RibbonBar;
import org.jetbrains.annotations.Nullable;

/**
 * LDLib2 {@link Editor} 子类 (spec §6.3)。
 *
 * <p>使用通用 RibbonBar (gui/ribbon/) 替代旧 KpRibbonBar。
 * RibbonBar 通过构造注入 ViewContextProvider + PreferenceStore, 不调 getInstance()。
 */
public class KpMapEditor extends Editor {

    @Nullable
    private MapPlaceholderView mapViewport;

    @Nullable
    private KpViewContextProvider kpViewContextProvider;

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

    @Override
    protected Editor createNewEditorInstance() {
        return new KpMapEditor();
    }

    @Override
    protected void initMenus() {
        // 不调用 super.initMenus() -- 不添加 FileMenu/ViewMenu
        menuContainer.clearAllChildren();

        // 构造 KP 适配层的 ViewContextProvider + PreferenceStore
        // 首版: ViewContextProvider 用 null editorScreen (getActiveContexts 返回空 Set)
        //       PreferenceStore 通过 KineticPlannerClient.CONFIG 获取真实 IKPConfig
        this.kpViewContextProvider = new KpViewContextProvider(null);
        var preferenceStore = new KPConfigRibbonPreferenceStore(getKpConfig());

        RibbonBar ribbonBar = new RibbonBar(kpViewContextProvider, preferenceStore);
        menuContainer.addChild(ribbonBar);
        top.getLayout().height(60);  // 60px (header 20 + content 40)
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
     * 放置自定义 View 到各 window。
     *
     * <p>必须在 {@link Editor} 构造完成（{@code rootWindow}/{@code leftWindow}/
     * {@code centerWindow}/{@code rightWindow} 已初始化）后调用。
     */
    public void placeCustomViews() {
        placeView(new ToolPanelView(), () -> leftWindow.getRightTop());
        this.mapViewport = new MapPlaceholderView();
        placeView(this.mapViewport, () -> centerWindow.getRightTop());
        centerWindow.getViewContainer().getStyle().backgroundTexture(IGuiTexture.EMPTY);
    }
}
