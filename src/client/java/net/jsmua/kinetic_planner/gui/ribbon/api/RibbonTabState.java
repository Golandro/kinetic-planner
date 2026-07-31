package net.jsmua.kinetic_planner.gui.ribbon.api;

/**
 * 选项卡运行时状态机 (spec §3.3)。
 *
 * <p>状态不变量:
 * <ul>
 *   <li>HIDDEN     -> contentVisible = false</li>
 *   <li>PINNED     -> contentVisible = true</li>
 *   <li>FLOATING   -> contentVisible 由用户交互控制</li>
 *   <li>CONTEXTUAL -> 头部可见性 = contextActive; contentVisible 遵循 PINNED/FLOATING 子模式</li>
 * </ul>
 *
 * <p><b>关键约束:</b> CONTEXTUAL 模式不响应 setDisplayMode (用户不可手动覆盖可见性)。
 */
public final class RibbonTabState {

    private final RibbonTabDefinition definition;
    private TabDisplayMode displayMode;
    private boolean contentVisible;
    private boolean contextActive;

    public RibbonTabState(RibbonTabDefinition definition) {
        this.definition = definition;
        this.displayMode = definition.getDefaultDisplayMode();
        this.contentVisible = (this.displayMode == TabDisplayMode.PINNED);
        this.contextActive = false;
    }

    public RibbonTabDefinition getDefinition() {
        return definition;
    }

    public TabDisplayMode getDisplayMode() {
        return displayMode;
    }

    public boolean getContentVisible() {
        return contentVisible;
    }

    /**
     * 设置显示模式 (用户控制)。
     * <p>CONTEXTUAL 模式不响应此方法 (可见性由 ViewContextProvider 驱动)。
     */
    public void setDisplayMode(TabDisplayMode mode) {
        if (this.displayMode == TabDisplayMode.CONTEXTUAL) {
            return;  // CONTEXTUAL 不可手动覆盖
        }
        this.displayMode = mode;
        this.contentVisible = (mode == TabDisplayMode.PINNED);
    }

    /** 仅 FLOATING 模式有效: toggle 内容面板可见性。 */
    public void toggleContentVisible() {
        if (displayMode == TabDisplayMode.FLOATING) {
            contentVisible = !contentVisible;
        }
    }

    /** 手动设置 contentVisible (FLOATING 浮层失焦收起等场景使用)。 */
    public void setContentVisible(boolean visible) {
        this.contentVisible = visible;
    }

    /**
     * 仅 CONTEXTUAL 模式: 视图上下文激活/停用。
     * <p>激活后 contentVisible 遵循 PINNED 子模式 = true (spec §10.2 getActiveSubMode 默认 PINNED)。
     */
    public void setContextActive(boolean active) {
        if (displayMode != TabDisplayMode.CONTEXTUAL) {
            return;  // 非 CONTEXTUAL 不响应
        }
        this.contextActive = active;
        if (active) {
            // 激活后遵循 PINNED 子模式
            this.contentVisible = true;
        } else {
            this.contentVisible = false;
        }
    }

    /** tab 头是否渲染。 */
    public boolean isHeaderVisible() {
        return displayMode != TabDisplayMode.HIDDEN
            && (displayMode != TabDisplayMode.CONTEXTUAL || contextActive);
    }
}
