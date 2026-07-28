package net.jsmua.kinetic_planner.command;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;

/**
 * /kp 命令处理器接口 - 按域分组。
 *
 * <p>定义在 main (common) sourceSet，命令树构建器 {@link KPCommandTree} 依赖此接口，
 * 不接触任何 client 类。实现类 {@code KPClientCommands} 在 client sourceSet 提供
 * 具体逻辑，GUI 相关的 edit/exit 委托给 {@code EditorCommands}。
 */
public interface KpCommandHandlers {

    // === /kp (root) ===
    int overview(CommandContext<CommandSourceStack> ctx);

    // === /kp overlay ===
    int overlayToggle(CommandContext<CommandSourceStack> ctx);
    int overlayEnable(CommandContext<CommandSourceStack> ctx);
    int overlayDisable(CommandContext<CommandSourceStack> ctx);
    int overlayReload(CommandContext<CommandSourceStack> ctx);
    int overlayStatus(CommandContext<CommandSourceStack> ctx);
    int overlayShowCreateToggle(CommandContext<CommandSourceStack> ctx);
    int overlayShowCreateSet(CommandContext<CommandSourceStack> ctx);

    // === /kp provider ===
    int providerList(CommandContext<CommandSourceStack> ctx);
    int providerEnable(CommandContext<CommandSourceStack> ctx);
    int providerDisable(CommandContext<CommandSourceStack> ctx);
    int providerSet(CommandContext<CommandSourceStack> ctx);
    int providerGetAll(CommandContext<CommandSourceStack> ctx);
    int providerGetParam(CommandContext<CommandSourceStack> ctx);
    int providerReset(CommandContext<CommandSourceStack> ctx);
    int providerResetCircuitAll(CommandContext<CommandSourceStack> ctx);
    int providerResetCircuitOne(CommandContext<CommandSourceStack> ctx);

    // === /kp theme ===
    int themeList(CommandContext<CommandSourceStack> ctx);
    int themeSet(CommandContext<CommandSourceStack> ctx);
    int themeReload(CommandContext<CommandSourceStack> ctx);
    int themeReset(CommandContext<CommandSourceStack> ctx);

    // === /kp debug ===
    int debugStats(CommandContext<CommandSourceStack> ctx);
    int debugDump(CommandContext<CommandSourceStack> ctx);
    int debugLayerCount(CommandContext<CommandSourceStack> ctx);
    int debugOverlayAnchors(CommandContext<CommandSourceStack> ctx);

    // === /kp edit / /kp exit ===
    int editMode(CommandContext<CommandSourceStack> ctx);
    int exitEditMode(CommandContext<CommandSourceStack> ctx);
}
