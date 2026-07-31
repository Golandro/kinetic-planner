package net.jsmua.kinetic_planner.gui.ribbon.api;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import net.jsmua.kinetic_planner.gui.ribbon.RibbonConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/**
 * 工具定义 (spec §4.7)。
 *
 * <p>Mod 注册的不可变数据, Ribbon 内部 Builder 将其转化为 UIElement。
 */
public interface RibbonToolDefinition {
    /** 唯一标识 (MC ResourceLocation, namespace:path 格式)。 */
    ResourceLocation getId();

    /** 显示名称。 */
    Component getDisplayName();

    /** tab 头图标 (可选)。 */
    Optional<IGuiTexture> getIcon();

    /** Tooltip (Phase 1 仅 TextTooltip)。 */
    Optional<TooltipContent> getTooltip();

    /** 快捷键显示标签 (如 "P", "Ctrl+S") -- 仅用于 tooltip 显示。 */
    Optional<String> getShortcutLabel();

    /**
     * KeyTip 序列 (如 ["P", "1"] 用于 Alt+P -> 1 二级导航) -- Phase 2+ 预留。
     * <p>首版返回 Optional.empty(); 保留字段避免后续加字段破坏 definition 实现 binary compat。
     */
    default Optional<List<String>> getKeyTips() { return Optional.empty(); }

    /** 工具尺寸。 */
    ToolSize getSize();

    /** 工具动作 (回调)。 */
    ToolAction getAction();

    /** 溢出权重: 数值越大越先被缩窄/隐藏。 */
    default int getOverflowWeight() { return RibbonConstants.DEFAULT_OVERFLOW_WEIGHT; }
}
