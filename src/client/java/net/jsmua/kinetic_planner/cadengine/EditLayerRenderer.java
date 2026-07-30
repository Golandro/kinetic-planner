package net.jsmua.kinetic_planner.cadengine;

import net.jsmua.kinetic_planner.KineticPlannerMod;
import net.jsmua.kinetic_planner.gui.editor.EditToolState;
import net.jsmua.kinetic_planner.instrument.GeometryCache;
import net.jsmua.kinetic_planner.instrument.WorldTreeReadOverlay;
import net.jsmua.kinetic_planner.projection.WorldScreenTransform;
import net.minecraft.client.gui.GuiGraphics;

import java.util.UUID;

/**
 * CAD 编辑图形层渲染器（spec §6.11）。
 *
 * <p>使用 {@link CADRenderEngine} 渲染：
 * <ol>
 *   <li>轨道拓扑（tracks + nodes + edgePoints）—— 与观看模式相同数据源</li>
 *   <li>编辑图形（selection / snap / preview / tool cursor）—— 新增</li>
 * </ol>
 *
 * <p>数据来源：{@link WorldTreeReadOverlay#getTransform()} / {@link WorldTreeReadOverlay#getGeometryCache()} /
 * {@link EditToolState}。
 *
 * <p>spec §6.11 + §3.2：编辑模式 CAD 由本类直接调用 CADRenderEngine 渲染，
 * 不走 XaeroMapRenderHook Mixin 路径（已被 isEditMode 守卫跳过）。
 */
public final class EditLayerRenderer {

    /**
     * 渲染编辑图形层。
     *
     * <p>由 KpEditorScreen.render() 在地图层之后、UI 层之前调用。
     *
     * @param gg           外部 GuiGraphics
     * @param editToolState 编辑会话状态
     */
    public static void render(GuiGraphics gg, EditToolState editToolState) {
        WorldScreenTransform transform = WorldTreeReadOverlay.getTransform();
        GeometryCache cache = WorldTreeReadOverlay.getGeometryCache();
        if (transform == null || cache == null) return;

        CADRenderEngine engine = WorldTreeReadOverlay.getEngine();
        try {
            engine.beginFrame(transform.cam().screenCenterX() * 2, transform.cam().screenCenterY() * 2, 1.0f);
            engine.applyWorldTransform(transform);

            // 1. 轨道拓扑（复用 WorldTreeReadOverlay 的渲染逻辑，审计 R3 修复）
            WorldTreeReadOverlay.renderTracks(engine, transform, cache);

            // 2. 编辑图形（selection / snap / preview / tool cursor）—— P2 后续实现
            renderEditGraphics(transform, cache, editToolState);

            engine.restoreWorldTransform();
            engine.endFrame();
        } catch (Throwable t) {
            KineticPlannerMod.LOGGER.error("EditLayerRenderer render failed", t);
            try { engine.endFrame(); } catch (Throwable ignored) {}
        }
    }

    /**
     * 渲染编辑图形：选择高亮 / snap 点 / 绘制预览 / 工具光标。
     */
    private static void renderEditGraphics(WorldScreenTransform transform,
                                           GeometryCache cache,
                                           EditToolState state) {
        // 选择集高亮：将选中节点用对比色绘制边框
        int selectionColor = 0xFFFF00FF;  // 紫红色
        for (UUID nodeId : state.getSelectedNodes()) {
            // 查找 nodeId 对应的节点位置（需 GraphGeometry 暴露 UUID 列表，当前未暴露）
            // P2 后续实现：扩展 GraphGeometry 暴露 node UUID 列表
        }

        // Snap 预览：若 tool == SNAP，在鼠标位置绘制十字光标
        if (state.getCurrentTool() == EditToolState.Tool.SNAP) {
            // 鼠标位置由 KpEditorScreen 传入或从 Minecraft.getInstance().mouseHandler 读取
            // P2 后续实现
        }
    }
}
