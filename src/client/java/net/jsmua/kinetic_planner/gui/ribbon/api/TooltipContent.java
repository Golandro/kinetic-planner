package net.jsmua.kinetic_planner.gui.ribbon.api;

/**
 * Tooltip 内容 (spec §4.6)。
 *
 * <p>Phase 1 仅支持 TextTooltip。WidgetTooltip 标记为 Phase 2+ 延后。
 */
public sealed interface TooltipContent permits TextTooltip {
}
