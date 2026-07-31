package net.jsmua.kinetic_planner.gui.ribbon.api;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * SimpleRibbonToolDefinition - RibbonToolDefinition 的简单 record 实现。
 *
 * <p>Mod 可直接使用此 record, 也可实现 RibbonToolDefinition 接口自定义。
 *
 * @param id            唯一标识
 * @param displayName   显示名称
 * @param icon          图标 (可选)
 * @param tooltip       Tooltip (可选)
 * @param shortcutLabel 快捷键标签 (可选)
 * @param size          工具尺寸
 * @param action        工具动作
 */
public record SimpleRibbonToolDefinition(
    ResourceLocation id,
    Component displayName,
    Optional<IGuiTexture> icon,
    Optional<TooltipContent> tooltip,
    Optional<String> shortcutLabel,
    ToolSize size,
    ToolAction action
) implements RibbonToolDefinition {
    @Override public ResourceLocation getId() { return id; }
    @Override public Component getDisplayName() { return displayName; }
    @Override public Optional<IGuiTexture> getIcon() { return icon; }
    @Override public Optional<TooltipContent> getTooltip() { return tooltip; }
    @Override public Optional<String> getShortcutLabel() { return shortcutLabel; }
    @Override public ToolSize getSize() { return size; }
    @Override public ToolAction getAction() { return action; }
}
