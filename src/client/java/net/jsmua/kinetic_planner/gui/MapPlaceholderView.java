package net.jsmua.kinetic_planner.gui;

import com.lowdragmc.lowdraglib2.editor.ui.View;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;

/**
 * 中心透明占位 View（spec §6.6）。
 *
 * <p>显式将背景纹理置为 {@link IGuiTexture#EMPTY}，
 * 保留默认 {@code allowHitTest = true} 以便主视口区域能命中并限制事件范围，
 * 但不注册事件监听器——导航/绘图逻辑仍由 {@link net.jsmua.kinetic_planner.gui.editor.KpEditorScreen} 统一路由。
 *
 * <p>保留为 {@code centerWindow} 中的单个 View，未来可通过同一 {@code ViewContainer}
 * 添加更多 Tab（分析面板等）共享主视口区域。
 *
 * <p>Layer 1（GuiMap 瓦片）+ Layer 2（CAD 编辑层）从此 View 区域天然透过。
 */
public class MapPlaceholderView extends View {

    public MapPlaceholderView() {
        super();
        getStyle().backgroundTexture(IGuiTexture.EMPTY);
    }

    public MapPlaceholderView(String name) {
        super(name);
        getStyle().backgroundTexture(IGuiTexture.EMPTY);
    }
}
