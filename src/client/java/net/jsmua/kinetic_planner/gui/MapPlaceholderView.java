package net.jsmua.kinetic_planner.gui;

import com.lowdragmc.lowdraglib2.editor.ui.View;

/**
 * 中心透明占位 View（spec §6.6）。
 *
 * <p>无背景纹理（View 默认 {@code IGuiTexture.EMPTY}），
 * 无事件监听器，唯一作用是占位让 Editor 布局正确。
 *
 * <p>Layer 1（GuiMap 瓦片）+ Layer 2（CAD 编辑层）从此 View 区域天然透过。
 */
public class MapPlaceholderView extends View {

    public MapPlaceholderView() {
        super();
    }

    public MapPlaceholderView(String name) {
        super(name);
    }
}
