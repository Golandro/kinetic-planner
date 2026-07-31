package net.jsmua.kinetic_planner.gui.ribbon.api;

import net.jsmua.kinetic_planner.gui.ribbon.RibbonConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/**
 * 工具组定义 (spec §5.1)。
 */
public interface RibbonToolGroupDefinition {
    /** 唯一标识 (MC ResourceLocation)。 */
    ResourceLocation getId();

    /** 底部标注 (可选, 为空则不标注)。 */
    Optional<Component> getDisplayName();

    /** 工具列表。 */
    List<RibbonToolDefinition> getTools();

    /** 溢出权重: 数值越大越先被缩窄。 */
    default int getOverflowWeight() { return RibbonConstants.DEFAULT_OVERFLOW_WEIGHT; }

    /**
     * Dialog Launcher: 组右下角小箭头按钮 -- Phase 2+ 预留。
     * <p>首版返回 Optional.empty()。
     */
    default Optional<RibbonCommand> getDialogLauncher() { return Optional.empty(); }
}
