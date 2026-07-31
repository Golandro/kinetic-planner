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
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
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
        menuContainer.clearAllChildren();

        // 构造 KP 适配层的 ViewContextProvider + PreferenceStore
        // ViewContextProvider 通过 supplier 读取本实例的 activeContexts (无静态状态)
        // PreferenceStore 通过 KineticPlannerClient.CONFIG 获取真实 IKPConfig
        this.kpViewContextProvider = new KpViewContextProvider(this::getActiveContexts);
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
