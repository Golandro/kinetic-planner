package net.jsmua.kinetic_planner.gui.editor;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import net.jsmua.kinetic_planner.KineticPlannerMod;
import net.jsmua.kinetic_planner.cadengine.CADRenderEngine;
import net.jsmua.kinetic_planner.cadengine.EditLayerRenderer;
import net.jsmua.kinetic_planner.config.KpClientState;
import net.jsmua.kinetic_planner.gui.event.KpUIEventForwarder;
import net.jsmua.kinetic_planner.instrument.OverlayDataProvider;
import net.jsmua.kinetic_planner.instrument.WorldTreeReadOverlay;
import net.jsmua.kinetic_planner.mapadapter.MapOverlayContextProvider;
import net.jsmua.kinetic_planner.mixin.XaeroMapAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import xaero.map.gui.GuiMap;

/**
 * 编辑模式 Screen 壳（spec §6.2 + §4.2）。
 *
 * <p>持有从观看模式传入的 {@link GuiMap} 实例，承载 {@link KpMapEditor}（LDLib2 Editor 子类）。
 * 三层渲染：① 地图层 → ② CAD 编辑层（Phase 6）→ ③ Editor UI 层。
 *
 * <p>事件路由：① forwarder 转发到 UI 树 → ② 未消费时按工具模式路由。
 * {@link EditToolState.Tool#NAVIGATION} 不再调用 {@link GuiMap#mouseClicked(double, double, int)} 等，
 * 改为直接操作 Xaero 相机字段平移/缩放，避免触发被 {@code XaeroUiSuppressMixin} 隐藏的原生 UI 按钮。
 *
 * <p>closeButton 链路：Editor.exit() → askToSaveProject() 跳过对话框（currentProject == null）
 * → ModularUI.getScreen().onClose() → 本类 onClose() → 切回 GuiMap。
 */
public class KpEditorScreen extends Screen implements MapOverlayContextProvider {

    private final GuiMap guiMap;
    private final KpMapEditor editor;
    private final ModularUI modularUI;
    private final KpUIEventForwarder eventForwarder;
    private final OverlayDataProvider overlayProvider;

    // 编辑模式自定义地图导航状态（替代直接转发给 GuiMap.mouseXXX，避免触发 Xaero 原生 UI 输入）
    private boolean isDraggingMap;

    /**
     * 静态工厂：从观看模式进入编辑模式。
     *
     * <p>spec §7.1：设置 isEditMode(true) → 构造 KpEditorScreen → setScreen。
     *
     * @param guiMap 当前观看模式的 GuiMap 实例（不销毁，仅切换 Screen 引用）
     * @return 构造完成的 KpEditorScreen（未 init）
     */
    public static KpEditorScreen create(GuiMap guiMap) {
        KpClientState.setEditMode(true);
        return new KpEditorScreen(guiMap);
    }

    private KpEditorScreen(GuiMap guiMap) {
        super(Component.literal("Kinetic Planner Editor"));
        this.guiMap = guiMap;
        // 构造时创建 editor + modularUI，避免 init() 重建丢失 View 状态（spec §6.2）
        this.editor = new KpMapEditor();
        this.editor.placeCustomViews();
        this.modularUI = ModularUI.of(UI.of(this.editor));
        // 绑定 Screen：让 modularUI.getScreen() 返回 this（closeButton 链路依赖）
        this.modularUI.setScreen(this);
        // forwarder 复用：编辑模式与观看模式共用同一事件转发逻辑（spec §6.9）
        this.eventForwarder = new KpUIEventForwarder(this.modularUI);
        // 测试缝：默认使用 WorldTreeReadOverlay 静态委托（审计 R5）
        this.overlayProvider = WorldTreeReadOverlay.asProvider();
    }

    @Override
    protected void init() {
        super.init();
        // modularUI.init 可安全重复调用（resize 时 MC 会再次调用 init）
        this.modularUI.init(this.width, this.height);
    }

    @Override
    public GuiMap getGuiMap() {
        return guiMap;
    }

    /**
     * 暴露 editor 实例（Phase 4 Ribbon / Phase 6 EditLayerRenderer 使用）。
     */
    public KpMapEditor getEditor() {
        return editor;
    }

    /**
     * 访问 Xaero GuiMap 的私有字段（cameraX/cameraZ/scale）。
     */
    private XaeroMapAccessor kp$accessor() {
        return (XaeroMapAccessor) guiMap;
    }

    /**
     * 判断鼠标是否落在中心主视口占位 View 的内容区域内。
     *
     * <p>用于限制地图导航/滚轮缩放只在主视口内生效，避免拖拽 Ribbon、工具栏等区域时误移相机。
     */
    private boolean isMouseOverMapViewport(double mouseX, double mouseY) {
        var viewport = editor.getMapViewport();
        return viewport != null && viewport.isMouseOver((float) mouseX, (float) mouseY);
    }

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTicks) {
        // ① 地图层渲染（Phase 3 实现）
        renderMapLayer(gg, mouseX, mouseY, partialTicks);
        // ② CAD 编辑层渲染
        EditLayerRenderer.render(gg, EditToolState.getInstance());
        // ③ Editor UI 层渲染
        eventForwarder.render(gg, mouseX, mouseY, partialTicks);
    }

    /**
     * 地图层渲染（Phase 3 Task 3.3 实现）。
     *
     * <p><b>方案 B'</b>（Task 3.1 研究结论）：GuiMap.render() 瓦片渲染完全内联，
     * 无法通过 @Invoker 单独调用。改为整体委托 {@code guiMap.render()}，
     * 由 {@link net.jsmua.kinetic_planner.mixin.XaeroUiSuppressMixin} 的
     * {@code @WrapOperation} 选择性抑制 UI 元素（雷达/HUD/按钮/右键菜单/Tooltip/消息框），
     * 保留瓦片底图与玩家箭头自然渲染。
     *
     * <p>KpEditorScreen 是活跃 Screen 时，MC 渲染循环调用本类 {@link #render}，
     * 不再自动调用 guiMap.render()。因此必须在此显式委托，否则编辑模式无地图底图。
     */
    private void renderMapLayer(GuiGraphics gg, int mouseX, int mouseY, float partialTicks) {
        guiMap.render(gg, mouseX, mouseY, partialTicks);
    }

    // === 事件路由（spec §4.2）===

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // ① Editor UI 优先
        if (eventForwarder.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        // ② 未消费 -> 按工具模式路由
        var tool = EditToolState.getInstance().getCurrentTool();
        if (tool == EditToolState.Tool.NAVIGATION && button == 0 && isMouseOverMapViewport(mouseX, mouseY)) {
            // 自定义导航：仅在主视口内、左键按下时开始拖拽，不透传给 GuiMap，避免触发 Xaero 原生 UI 按钮。
            isDraggingMap = true;
            return true;
        }
        // 其他工具 -> CADRenderEngine 命中检测
        var transform = overlayProvider.getTransform();
        var cache = overlayProvider.getGeometryCache();
        if (transform == null || cache == null) return false;
        // CADRenderEngine 实例由 EditLayerRenderer 持有，此处通过 EditLayerRenderer 转发
        // 或直接持有 CADRenderEngine 实例（简化）
        return false;  // P2 后续：EditLayerRenderer.handleClick
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (eventForwarder.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        var tool = EditToolState.getInstance().getCurrentTool();
        if (tool == EditToolState.Tool.NAVIGATION && isDraggingMap) {
            isDraggingMap = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double dragX, double dragY) {
        if (eventForwarder.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        // 右键取消拖拽（spec §4.3.1：拖拽中按右键直接结束）
        if (isDraggingMap && button == 1) {
            isDraggingMap = false;
            return true;
        }
        // 自定义拖拽平移：保持拖拽状态即可（鼠标已移出主视口也继续，符合标准地图交互）。
        if (isDraggingMap) {
            var accessor = kp$accessor();
            // scale 表示 pixels per block，因此世界坐标 delta = 屏幕像素 delta / scale。
            double blocksPerPixel = 1.0 / accessor.kp$scale();
            accessor.kp$setCameraX(accessor.kp$cameraX() - dragX * blocksPerPixel);
            accessor.kp$setCameraZ(accessor.kp$cameraZ() - dragY * blocksPerPixel);
            return true;
        }
        // Draw 工具 -> CADRenderEngine.handleDrag (Phase 6)
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (eventForwarder.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        // 所有工具下滚轮均缩放（spec §4.3.2 CAD 标准行为）
        if (isMouseOverMapViewport(mouseX, mouseY)) {
            // 优先委托 guiMap.mouseScrolled()，复用 Xaero 原生缩放逻辑
            // （中心点保持 + 瓦片降级），spec §4.3.2 + §5.3
            try {
                if (guiMap.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
                    return true;
                }
            } catch (Exception e) {
                // guiMap.mouseScrolled 可能因 mc.screen 检查失败，降级为 accessor 方案
                KineticPlannerMod.LOGGER.debug("guiMap.mouseScrolled delegation failed, falling back to accessor", e);
            }
            // 降级方案：直接读写 scale 字段（无中心点保持，仅作为 fallback）
            var accessor = kp$accessor();
            double currentScale = accessor.kp$scale();
            double newScale = currentScale * (scrollY > 0 ? 1.2 : 1.0 / 1.2);
            accessor.kp$setScale(newScale);
            return true;
        }
        return false;
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        eventForwarder.mouseMoved(mouseX, mouseY);
        // NAVIGATION 工具不再转发 mouseMoved 给 GuiMap，避免触发 Xaero 悬停 UI。
        // Draw/Snap -> CADRenderEngine.handleHover (Phase 6)
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (eventForwarder.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        // ESC 退出编辑模式（spec §4.4）
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        // 工具快捷键：从 Tool 枚举元数据匹配（审计 R2 修复，消除散弹式修改）
        var state = EditToolState.getInstance();
        EditToolState.Tool matchedTool = EditToolState.Tool.fromKeyCode(keyCode);
        if (matchedTool != null) {
            state.setCurrentTool(matchedTool);
            return true;
        }
        // NAVIGATION 工具下，未消费的键盘事件委托 guiMap
        if (state.getCurrentTool() == EditToolState.Tool.NAVIGATION) {
            return guiMap.keyPressed(keyCode, scanCode, modifiers);
        }
        return false;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (eventForwarder.keyReleased(keyCode, scanCode, modifiers)) {
            return true;
        }
        var tool = EditToolState.getInstance().getCurrentTool();
        if (tool == EditToolState.Tool.NAVIGATION) {
            return guiMap.keyReleased(keyCode, scanCode, modifiers);
        }
        return false;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (eventForwarder.charTyped(codePoint, modifiers)) {
            return true;
        }
        var tool = EditToolState.getInstance().getCurrentTool();
        if (tool == EditToolState.Tool.NAVIGATION) {
            return guiMap.charTyped(codePoint, modifiers);
        }
        return false;
    }

    @Override
    public void onClose() {
        KpClientState.setEditMode(false);
        Minecraft.getInstance().setScreen(guiMap);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
