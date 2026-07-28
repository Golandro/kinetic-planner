package net.jsmua.kinetic_planner.config;

import net.jsmua.kinetic_planner.instrument.WorldTreeReadOverlay;

/**
 * 叠加层状态管理器。
 *
 * <p>封装叠加层开关状态、重载逻辑和状态查询。命令层（{@link KPCommands}）
 * 通过此类操作叠加层，不直接访问 {@link KPConfig} 或 {@link WorldTreeReadOverlay}。
 *
 * <h2>职责分离</h2>
 * <ul>
 *   <li>状态读写：委托 {@link KPConfig#OVERLAY_ENABLED}</li>
 *   <li>重载：从当前配置值重建 {@link net.jsmua.kinetic_planner.cadengine Theme} 并应用到渲染层</li>
 *   <li>状态查询：从 {@link WorldTreeReadOverlay} 获取图/节点/边统计数据</li>
 * </ul>
 *
 * <h2>已知限制</h2>
 * <p>NeoForge 1.21.1 不支持运行时 TOML 文件重载。{@link #reload()} 从内存中的
 * 配置值重建 Theme。如需从磁盘重载配置，请使用 NeoForge 的配置管理或重启游戏。
 */
public final class OverlayControl {

    private static final IKPConfig config = KPConfig.getInstance();

    private OverlayControl() {}

    /**
     * 叠加层是否启用。
     *
     * @return {@link KPConfig#OVERLAY_ENABLED} 的当前值
     */
    public static boolean isEnabled() {
        return config.isOverlayEnabled();
    }

    /**
     * 启用叠加层。
     */
    public static void enable() {
        config.setOverlayEnabled(true);
    }

    /**
     * 禁用叠加层。
     */
    public static void disable() {
        config.setOverlayEnabled(false);
    }

    /**
     * 切换叠加层开关。
     */
    public static void toggle() {
        config.setOverlayEnabled(!isEnabled());
    }

    /**
     * 重载叠加层：从当前配置值重建 Theme 并应用到渲染层。
     *
     * <p>注意：NeoForge 1.21.1 不支持运行时 TOML 文件重载。
     * 此方法从内存中的配置值重建 Theme。如需从磁盘重载配置，
     * 请使用 NeoForge 的配置管理或重启游戏。
     */
    public static void reload() {
        WorldTreeReadOverlay.setTheme(config.toTheme());
    }

    /**
     * 查询是否显示 Create 的列车地图叠加层。
     *
     * @return {@code true} 如果配置为显示（叠加层启用时 Create 的 renderAndPick 正常执行）；
     *         {@code false} 表示阻止（由 Mixin 拦截 Create 的 renderAndPick，KP 完全替代）
     */
    public static boolean isShowCreateTrackMap() {
        return config.isShowCreateTrackMap();
    }

    /**
     * 设置是否显示 Create 的列车地图叠加层。
     *
     * @param show 是否显示
     */
    public static void setShowCreateTrackMap(boolean show) {
        config.setShowCreateTrackMap(show);
    }

    /**
     * 返回叠加层状态字符串（用于命令输出）。
     *
     * <p>格式：
     * <ul>
     *   <li>地图打开时：{@code "[KP] Overlay: ON | Graphs: 2 | Nodes: 15 | Edges: 12"}</li>
     *   <li>地图关闭时：{@code "[KP] Overlay: ON | Map: closed (no data)"}</li>
     * </ul>
     *
     * @return 格式化的状态字符串
     */
    public static String status() {
        boolean enabled = isEnabled();
        int graphCount = WorldTreeReadOverlay.getGraphCount();
        if (graphCount == 0) {
            return String.format("[KP] Overlay: %s | Map: closed (no data)",
                enabled ? "ON" : "OFF");
        }
        int nodeCount = WorldTreeReadOverlay.getNodeCount();
        int edgeCount = WorldTreeReadOverlay.getEdgeCount();
        return String.format("[KP] Overlay: %s | Graphs: %d | Nodes: %d | Edges: %d",
            enabled ? "ON" : "OFF", graphCount, nodeCount, edgeCount);
    }
}
