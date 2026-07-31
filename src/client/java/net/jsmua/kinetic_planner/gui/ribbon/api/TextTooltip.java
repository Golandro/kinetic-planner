package net.jsmua.kinetic_planner.gui.ribbon.api;

import net.minecraft.network.chat.Component;

/** MC Component 文本 tooltip (spec §4.6, Phase 1 实现)。 */
public record TextTooltip(Component text) implements TooltipContent {
}
