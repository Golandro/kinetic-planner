package net.jsmua.kinetic_planner.mapadapter;

import net.jsmua.kinetic_planner.gui.editor.KpEditorScreen;
import xaero.map.gui.GuiMap;

import javax.annotation.Nullable;

/**
 * 暴露持有者关联的 {@link GuiMap} 实例（spec §6.10）。
 *
 * <p>由 {@link KpEditorScreen} 实现，
 * 让 {@link XaeroMapOverlayProvider} 通过接口（非 instanceof）获取编辑模式下的 GuiMap。
 *
 * <p>观看模式下 Screen 直接是 GuiMap；编辑模式下 Screen 是 KpEditorScreen，
 * 通过此接口暴露内部持有的 GuiMap。
 */
public interface MapOverlayContextProvider {

    /**
     * 返回当前关联的 GuiMap 实例。
     *
     * @return GuiMap 实例；未持有时返回 null
     */
    @Nullable
    GuiMap getGuiMap();
}
