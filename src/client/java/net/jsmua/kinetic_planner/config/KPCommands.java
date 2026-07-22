package net.jsmua.kinetic_planner.config;

import net.jsmua.kinetic_planner.KineticPlannerMod;
import net.jsmua.kinetic_planner.instrument.WorldTreeReadOverlay;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * /kp 命令体系注册。
 *
 * <p>命令树：
 * <pre>
 * /kp overlay toggle   -- 切换叠加开关
 * /kp overlay reload   -- 重载配置 + 主题
 * /kp theme reload     -- 仅重载主题
 * /kp theme list       -- 列出可用主题
 * /kp debug stats      -- 输出渲染统计
 * </pre>
 */
public final class KPCommands {

    private KPCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("kp")
            .then(Commands.literal("overlay")
                .then(Commands.literal("toggle")
                    .executes(KPCommands::overlayToggle))
                .then(Commands.literal("reload")
                    .executes(KPCommands::overlayReload)))
            .then(Commands.literal("theme")
                .then(Commands.literal("reload")
                    .executes(KPCommands::themeReload))
                .then(Commands.literal("list")
                    .executes(KPCommands::themeList)))
            .then(Commands.literal("debug")
                .then(Commands.literal("stats")
                    .executes(KPCommands::debugStats)))
        );
    }

    private static int overlayToggle(CommandContext<CommandSourceStack> ctx) {
        boolean current = KPConfig.OVERLAY_ENABLED.get();
        KPConfig.OVERLAY_ENABLED.set(!current);
        ctx.getSource().sendSuccess(() ->
            Component.literal("[KP] Overlay " + (!current ? "enabled" : "disabled")), false);
        return 1;
    }

    private static int overlayReload(CommandContext<CommandSourceStack> ctx) {
        WorldTreeReadOverlay.setTheme(KPConfig.toTheme());
        ctx.getSource().sendSuccess(() ->
            Component.literal("[KP] Config and theme reloaded"), false);
        return 1;
    }

    private static int themeReload(CommandContext<CommandSourceStack> ctx) {
        WorldTreeReadOverlay.setTheme(KPConfig.toTheme());
        ctx.getSource().sendSuccess(() ->
            Component.literal("[KP] Theme reloaded: " + KPConfig.THEME_ACTIVE.get()), false);
        return 1;
    }

    private static int themeList(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() ->
            Component.literal("[KP] Available themes: default"), false);
        return 1;
    }

    private static int debugStats(CommandContext<CommandSourceStack> ctx) {
        KineticPlannerMod.LOGGER.info("[KP] Debug stats requested");
        ctx.getSource().sendSuccess(() ->
            Component.literal("[KP] Debug stats: see console log"), false);
        return 1;
    }
}
