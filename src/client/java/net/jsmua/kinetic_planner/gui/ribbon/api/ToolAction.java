package net.jsmua.kinetic_planner.gui.ribbon.api;

/**
 * 工具动作 (spec §4.4)。
 *
 * <p>Phase 1 仅 permits ButtonAction + SeparatorAction。
 * SplitButtonAction / DropdownAction 标记为 Phase 2+ 延后 (YAGNI)。
 */
public sealed interface ToolAction permits ButtonAction, SeparatorAction {
}
