package net.jsmua.kinetic_planner.config;

/**
 * KP 客户端 UI 全局状态 - 配置面板可见性。
 *
 * <p>spec §9.3：管理配置面板可见性状态（静态，Xaero Mixin 与 JM Plugin 共享）。
 * 两侧挂载点读取同一状态，避免各自维护 visible 字段导致状态分裂。
 *
 * <p>该类无 MC 依赖（纯静态布尔状态），可在纯 JVM 单元测试。
 */
public final class KpClientState {

    private static boolean configPanelVisible = false;

    private KpClientState() {}

    /**
     * 配置面板是否可见。
     *
     * @return true 如果面板当前应渲染
     */
    public static boolean isConfigPanelVisible() {
        return configPanelVisible;
    }

    /**
     * 设置配置面板可见性。
     *
     * @param visible true 显示面板；false 隐藏
     */
    public static void setConfigPanelVisible(boolean visible) {
        configPanelVisible = visible;
    }

    /**
     * 切换配置面板可见性。
     */
    public static void toggleConfigPanel() {
        configPanelVisible = !configPanelVisible;
    }
}
