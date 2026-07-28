package net.jsmua.kinetic_planner.config;

/**
 * KP 客户端 UI 全局状态 - 配置面板可见性 + 编辑模式标志。
 *
 * <p>spec §9.3：管理配置面板可见性状态（静态，Xaero Mixin 与 JM Plugin 共享）。
 * 两侧挂载点读取同一状态，避免各自维护 visible 字段导致状态分裂。
 *
 * <p>spec §1.3 双模式架构：editMode 标志区分观看模式与编辑模式，Mixin 守卫读取此值
 * 决定是否跳过观看模式渲染/事件路径。
 *
 * <p>该类无 MC 依赖（纯静态布尔状态），可在纯 JVM 单元测试。
 */
public final class KpClientState {

    private static boolean configPanelVisible = false;

    /**
     * 是否处于编辑模式（spec §1.3 双模式架构）。
     *
     * <p>全局模式标志：Mixin 守卫读取此值决定是否跳过观看模式渲染/事件路径。
     * 与 {@link EditToolState}（编辑会话状态）生命周期不同——后者仅在编辑模式活跃。
     */
    private static boolean editMode = false;

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

    /**
     * 是否处于编辑模式。
     *
     * @return true 如果当前 Screen 为 KpEditorScreen（编辑模式活跃）
     */
    public static boolean isEditMode() {
        return editMode;
    }

    /**
     * 设置编辑模式状态。
     *
     * <p>由 KpEditorScreen.create/onClose 调用。Mixin 守卫读取此值跳过观看模式路径。
     *
     * @param mode true 进入编辑模式；false 退出
     */
    public static void setEditMode(boolean mode) {
        editMode = mode;
    }
}
