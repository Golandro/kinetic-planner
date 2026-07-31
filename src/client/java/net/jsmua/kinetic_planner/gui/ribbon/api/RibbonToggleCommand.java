package net.jsmua.kinetic_planner.gui.ribbon.api;

/** 开关型命令 (spec §4.3)。 */
public interface RibbonToggleCommand extends RibbonCommand {
    /** 当前是否激活。 */
    boolean isActive();

    /** 设置激活状态。 */
    void setActive(boolean active);
}
