package net.jsmua.kinetic_planner.instrument;

import net.jsmua.kinetic_planner.projection.WorldScreenTransform;

/**
 * 叠加层数据提供者接口（审计 R5 测试缝修复）。
 *
 * <p>将 {@link WorldTreeReadOverlay} 的静态访问器抽象为接口，
 * 使 {@link net.jsmua.kinetic_planner.gui.editor.KpEditorScreen} 和
 * {@link net.jsmua.kinetic_planner.cadengine.EditLayerRenderer}
 * 可在测试中注入 mock 实现。
 *
 * <p>生产环境使用 {@link WorldTreeReadOverlay#asProvider()} 作为默认实现（静态委托）。
 */
public interface OverlayDataProvider {

    /**
     * 返回当前世界-屏幕变换。
     *
     * @return 当前变换；地图未打开时为 null
     */
    WorldScreenTransform getTransform();

    /**
     * 返回当前几何缓存。
     *
     * @return 几何缓存实例
     */
    GeometryCache getGeometryCache();
}
