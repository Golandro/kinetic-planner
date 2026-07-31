package net.jsmua.kinetic_planner.gui.ribbon.api;

/**
 * 普通按钮 / 开关按钮 (spec §4.4, Phase 1)。
 *
 * @param command 按钮回调; toggle=true 时必须为 RibbonToggleCommand
 * @param toggle  true = 开关型按钮
 */
public record ButtonAction(RibbonCommand command, boolean toggle) implements ToolAction {
}
