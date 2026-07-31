package net.jsmua.kinetic_planner.gui;

import com.lowdragmc.lowdraglib2.editor.ui.View;
import com.lowdragmc.lowdraglib2.editor.ui.ViewContainer;
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

    /**
     * 清空 ViewContainer -> TabView -> tabContentContainer -> tabHeaderContainer
     * 背景链, 确保地图占位区域全透明 (spec §6.6, Task 4)。
     *
     * <p>仅作用于传入的 {@code container}, 不影响其他 window (如左侧工具面板),
     * 因此 tab 头部 (leftWindow) 仍保持不透明, 仅 centerWindow 的链路被清空。
     *
     * @param container 中心 window 的 ViewContainer (运行时由 KpMapEditor.placeCustomViews 传入)
     */
    public static void prepareTransparentChain(ViewContainer container) {
        container.getStyle().backgroundTexture(IGuiTexture.EMPTY);
        container.tabView.getStyle().backgroundTexture(IGuiTexture.EMPTY);
        container.tabView.tabContentContainer.getStyle().backgroundTexture(IGuiTexture.EMPTY);
        container.tabView.tabHeaderContainer.getStyle().backgroundTexture(IGuiTexture.EMPTY);
    }
}
