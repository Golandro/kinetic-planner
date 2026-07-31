package net.jsmua.kinetic_planner.gui.ribbon.api;

/**
 * 选项卡显示模式 (spec §3.1)。
 *
 * <p>核心模式（PINNED/FLOATING/HIDDEN）由用户控制；CONTEXTUAL 由视图上下文驱动。
 */
public enum TabDisplayMode {
    /** 固定: 内容面板始终可见, 占据布局空间 (标准 Ribbon)。适配 CAD 任务驱动工作流。 */
    PINNED,

    /** 浮动: 内容面板浮层弹出, 失焦自动收起, 不占布局空间。适配低频任务组。 */
    FLOATING,

    /** 隐藏: tab 头不渲染, 内容不可访问 (用户可在设置中恢复)。 */
    HIDDEN,

    /** 上下文: tab 头可见性由 ViewContextProvider 决定。仅用于视图驱动场景, 不适配对象选择编辑。 */
    CONTEXTUAL
}
