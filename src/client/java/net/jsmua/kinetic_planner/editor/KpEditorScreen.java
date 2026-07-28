package net.jsmua.kinetic_planner.editor;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import net.jsmua.kinetic_planner.KineticPlannerMod;
import net.jsmua.kinetic_planner.config.KpClientState;
import net.jsmua.kinetic_planner.config.KpUIEventForwarder;
import net.jsmua.kinetic_planner.mapadapter.MapOverlayContextProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import xaero.map.gui.GuiMap;

import javax.annotation.Nullable;

/**
 * 编辑模式 Screen 壳（spec §6.2）。
 *
 * <p>持有从观看模式传入的 {@link GuiMap} 实例，承载 {@link KpMapEditor}（LDLib2 Editor 子类）。
 * 三层渲染：① 地图层 → ② CAD 编辑层（Phase 6）→ ③ Editor UI 层。
 *
 * <p>事件路由：① forwarder 转发到 UI 树 → ② 未消费时按工具模式路由到 guiMap 或 CAD（Phase 5）。
 *
 * <p>closeButton 链路：Editor.exit() → askToSaveProject() 跳过对话框（currentProject == null）
 * → ModularUI.getScreen().onClose() → 本类 onClose() → 切回 GuiMap。
 */
public class KpEditorScreen extends Screen implements MapOverlayContextProvider {

    private final GuiMap guiMap;
    private final KpMapEditor editor;
    private final ModularUI modularUI;
    private final KpUIEventForwarder eventForwarder;

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

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTicks) {
        // ① 地图层渲染（Phase 3 实现）
        renderMapLayer(gg, mouseX, mouseY, partialTicks);
        // ② CAD 编辑层渲染（Phase 6 实现）
        // EditLayerRenderer.render(gg, guiMap, editToolState);
        // ③ Editor UI 层渲染
        eventForwarder.render(gg, mouseX, mouseY, partialTicks);
    }

    /**
     * 地图层渲染（Phase 3 Task 3.3 实现）。
     *
     * <p>方案 B：手动调用 GuiMap 内部瓦片/路标渲染方法。
     */
    private void renderMapLayer(GuiGraphics gg, int mouseX, int mouseY, float partialTicks) {
        // Phase 3 实现
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
