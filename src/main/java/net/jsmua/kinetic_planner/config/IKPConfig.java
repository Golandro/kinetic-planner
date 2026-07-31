package net.jsmua.kinetic_planner.config;

import net.jsmua.kinetic_planner.cadengine.Theme;
import net.jsmua.kinetic_planner.data.ProviderConfig;

/**
 * KP 配置中介接口 - 桥接数据配置、客户端配置至不同 GUI。
 *
 * <p>封装 NeoForge {@code ModConfigSpec} 实现细节，外部仅通过领域方法访问配置值。
 * 实现类 {@code KPConfig} 在 client sourceSet，持有 {@code ModConfigSpec} 实例。
 *
 * <p>该接口位于 main (common) sourceSet，不引用任何 client 类，
 * 允许 common 代码依赖配置契约（如果未来需要）。
 */
public interface IKPConfig {

    // ===== [overlay] =====

    boolean isOverlayEnabled();

    void setOverlayEnabled(boolean enabled);

    boolean isShowCreateTrackMap();

    void setShowCreateTrackMap(boolean show);

    // ===== [theme] =====

    String getActiveThemeName();

    void setActiveThemeName(String name);

    boolean isConstantScreenLineWidth();

    void setConstantScreenLineWidth(boolean value);

    double getFixedScreenLineWidthPx();

    void setFixedScreenLineWidthPx(double value);

    // ===== [layers] =====

    boolean isLayerTracksVisible();

    void setLayerTracksVisible(boolean value);

    boolean isLayerNodesVisible();

    void setLayerNodesVisible(boolean value);

    boolean isLayerEdgePointsVisible();

    void setLayerEdgePointsVisible(boolean value);

    // ===== [label] =====

    boolean isShowNodeLabels();

    void setShowNodeLabels(boolean value);

    boolean isShowStationNames();

    void setShowStationNames(boolean value);

    // ===== [provider] =====

    /**
     * 查询指定 provider 的配置（合并 TOML 值与默认值）。
     *
     * @param modId provider mod ID
     * @return 配置；未知 modId 且未注册返回 null
     */
    ProviderConfig getProviderConfig(String modId);

    /**
     * 设置 provider 的 enabled 状态。
     *
     * @return true 如果设置成功（modId 已知）
     */
    boolean setProviderEnabled(String modId, boolean enabled);

    /**
     * 设置 provider 的视觉参数。
     *
     * @param param 参数名（lineWidthScale / alphaScale / dashed / priority）
     * @param value 字符串形式的新值
     * @return true 如果设置成功
     */
    boolean setProviderParam(String modId, String param, String value);

    // ===== [ribbon] =====

    /** 获取指定 tab 的显示模式字符串 (PINNED/FLOATING/HIDDEN/CONTEXTUAL), 未配置返回 null。 */
    String getRibbonTabDisplayMode(String tabId);

    /** 设置指定 tab 的显示模式字符串。 */
    void setRibbonTabDisplayMode(String tabId, String mode);

    /** 获取 QAT 工具 ID 字符串列表 (如 ["kp:tool_pan", "kp:tool_select"])。 */
    java.util.List<String> getRibbonQatToolIds();

    /** 设置 QAT 工具 ID 字符串列表。 */
    void setRibbonQatToolIds(java.util.List<String> ids);

    /** 获取上次选中的 tab ID 字符串 (如 "kp:tools"), 未配置返回 null。 */
    String getRibbonSelectedTab();

    /** 设置上次选中的 tab ID 字符串。 */
    void setRibbonSelectedTab(String tabId);

    // ===== [theme conversion] =====

    /**
     * 从配置值构建 {@link Theme} record。
     */
    Theme toTheme();
}
