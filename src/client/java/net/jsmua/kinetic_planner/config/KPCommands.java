package net.jsmua.kinetic_planner.config;

import net.jsmua.kinetic_planner.instrument.WorldTreeReadOverlay;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.network.chat.Component;

/**
 * /kp 命令体系注册（P0 完整版，14 个命令节点）。
 *
 * <p>通过 {@link net.neoforged.neoforge.client.event.RegisterClientCommandsEvent}
 * 注册为客户端命令。命令逻辑委托 {@link OverlayControl} / {@link ThemeManager} /
 * {@link WorldTreeReadOverlay}，此类仅负责命令分发。
 *
 * <h2>命令树</h2>
 * <pre>
 * /kp                         -- 综合概览
 * /kp overlay toggle          -- 切换叠加开关
 * /kp overlay enable          -- 显式开启
 * /kp overlay disable         -- 显式关闭
 * /kp overlay reload          -- 从配置值重建 Theme 并应用
 * /kp overlay status          -- 查看状态（开关/图数/节点数/边数）
 * /kp theme list              -- 扫描目录列出可用主题
 * /kp theme set &lt;name&gt;        -- 切换到指定主题（从 JSON 文件加载）
 * /kp theme reload            -- 从磁盘重载当前主题 JSON
 * /kp theme reset             -- 重置为默认主题
 * /kp debug stats             -- 渲染统计（图/节点/边/边点数）
 * /kp debug dump              -- 转储 TrackGraph 数据到日志
 * /kp debug layer-count       -- 各图层对象计数
 * /kp debug overlay-anchors   -- 叠加层锚点诊断（相机/缩放/屏幕/维度）
 * </pre>
 *
 * <p>NeoForge 1.21.1 的 {@code RegisterClientCommandsEvent.getDispatcher()} 返回
 * {@code CommandDispatcher<CommandSourceStack>}（不是 ClientCommandSourceStack），
 * 因此命令处理器类型签名与服务端命令一致。
 */
public final class KPCommands {

    private KPCommands() {}

    /**
     * 注册 /kp 命令树。
     *
     * <p>由 {@link KineticPlannerClient#onRegisterClientCommands} 通过
     * {@link net.neoforged.neoforge.client.event.RegisterClientCommandsEvent} 调用。
     *
     * @param dispatcher 客户端命令调度器
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("kp")
            .executes(KPCommands::overview)
            .then(Commands.literal("overlay")
                .then(Commands.literal("toggle")
                    .executes(KPCommands::overlayToggle))
                .then(Commands.literal("enable")
                    .executes(KPCommands::overlayEnable))
                .then(Commands.literal("disable")
                    .executes(KPCommands::overlayDisable))
                .then(Commands.literal("reload")
                    .executes(KPCommands::overlayReload))
                .then(Commands.literal("status")
                    .executes(KPCommands::overlayStatus)))
            .then(Commands.literal("theme")
                .then(Commands.literal("list")
                    .executes(KPCommands::themeList))
                .then(Commands.literal("set")
                    .then(Commands.argument("name", StringArgumentType.word())
                        .executes(KPCommands::themeSet)))
                .then(Commands.literal("reload")
                    .executes(KPCommands::themeReload))
                .then(Commands.literal("reset")
                    .executes(KPCommands::themeReset)))
            .then(Commands.literal("debug")
                .then(Commands.literal("stats")
                    .executes(KPCommands::debugStats))
                .then(Commands.literal("dump")
                    .executes(KPCommands::debugDump))
                .then(Commands.literal("layer-count")
                    .executes(KPCommands::debugLayerCount))
                .then(Commands.literal("overlay-anchors")
                    .executes(KPCommands::debugOverlayAnchors)))
        );
    }

    // === /kp (root) -- 综合概览 ===

    /**
     * {@code /kp}（无参数）：显示叠加层状态概览。
     */
    private static int overview(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() ->
            Component.literal(OverlayControl.status()), false);
        return 1;
    }

    // === /kp overlay -- 叠加层控制 ===

    /**
     * {@code /kp overlay toggle}：切换叠加层开关。
     */
    private static int overlayToggle(CommandContext<CommandSourceStack> ctx) {
        OverlayControl.toggle();
        ctx.getSource().sendSuccess(() ->
            Component.literal("[KP] Overlay " + (OverlayControl.isEnabled() ? "enabled" : "disabled")), false);
        return 1;
    }

    /**
     * {@code /kp overlay enable}：显式启用叠加层。
     */
    private static int overlayEnable(CommandContext<CommandSourceStack> ctx) {
        OverlayControl.enable();
        ctx.getSource().sendSuccess(() ->
            Component.literal("[KP] Overlay enabled"), false);
        return 1;
    }

    /**
     * {@code /kp overlay disable}：显式关闭叠加层。
     */
    private static int overlayDisable(CommandContext<CommandSourceStack> ctx) {
        OverlayControl.disable();
        ctx.getSource().sendSuccess(() ->
            Component.literal("[KP] Overlay disabled"), false);
        return 1;
    }

    /**
     * {@code /kp overlay reload}：从当前配置值重建 Theme 并应用到渲染层。
     *
     * <p>注意：NeoForge 1.21.1 不支持运行时 TOML 重载，此命令从内存配置值重建。
     */
    private static int overlayReload(CommandContext<CommandSourceStack> ctx) {
        OverlayControl.reload();
        ctx.getSource().sendSuccess(() ->
            Component.literal("[KP] Config reloaded and theme rebuilt"), false);
        return 1;
    }

    /**
     * {@code /kp overlay status}：查看叠加层状态。
     */
    private static int overlayStatus(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() ->
            Component.literal(OverlayControl.status()), false);
        return 1;
    }

    // === /kp theme -- 主题管理 ===

    /**
     * {@code /kp theme list}：扫描主题目录，列出可用主题。
     */
    private static int themeList(CommandContext<CommandSourceStack> ctx) {
        var themes = ThemeManager.listThemeNames(ThemeManager.getThemesDir());
        String current = KPConfig.THEME_ACTIVE.get();
        ctx.getSource().sendSuccess(() ->
            Component.literal("[KP] Themes: " + String.join(", ", themes) +
                " (active: " + current + ")"), false);
        return 1;
    }

    /**
     * {@code /kp theme set <name>}：切换到指定主题。
     *
     * <p>"default" 使用内置默认主题，其他名称从 {@code config/kineticplanner/themes/<name>.json} 加载。
     * 加载失败时提示用户使用 {@code /kp theme list} 查看可用主题。
     */
    private static int themeSet(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        if (ThemeManager.setTheme(name)) {
            ctx.getSource().sendSuccess(() ->
                Component.literal("[KP] Theme set to: " + name), false);
            return 1;
        } else {
            ctx.getSource().sendFailure(
                Component.literal("[KP] Theme not found: " + name +
                    ". Use /kp theme list to see available themes."));
            return 0;
        }
    }

    /**
     * {@code /kp theme reload}：从磁盘重载当前主题 JSON。
     *
     * <p>如果当前主题是 "default"，重置为内置默认值。
     * 否则从 JSON 文件重新加载。文件已删除时回退到默认。
     */
    private static int themeReload(CommandContext<CommandSourceStack> ctx) {
        ThemeManager.reload();
        ctx.getSource().sendSuccess(() ->
            Component.literal("[KP] Theme reloaded: " + KPConfig.THEME_ACTIVE.get()), false);
        return 1;
    }

    /**
     * {@code /kp theme reset}：重置为默认主题。
     */
    private static int themeReset(CommandContext<CommandSourceStack> ctx) {
        ThemeManager.reset();
        ctx.getSource().sendSuccess(() ->
            Component.literal("[KP] Theme reset to default"), false);
        return 1;
    }

    // === /kp debug -- 调试诊断 ===

    /**
     * {@code /kp debug stats}：输出渲染统计（图/节点/边/边点数量）。
     */
    private static int debugStats(CommandContext<CommandSourceStack> ctx) {
        int graphs = WorldTreeReadOverlay.getGraphCount();
        int nodes = WorldTreeReadOverlay.getNodeCount();
        int edges = WorldTreeReadOverlay.getEdgeCount();
        int edgePoints = WorldTreeReadOverlay.getEdgePointCount();
        ctx.getSource().sendSuccess(() ->
            Component.literal(String.format(
                "[KP] Stats | Graphs: %d | Nodes: %d | Edges: %d | EdgePoints: %d",
                graphs, nodes, edges, edgePoints)), false);
        return 1;
    }

    /**
     * {@code /kp debug dump}：将当前维度 TrackGraph 数据转储到控制台日志。
     */
    private static int debugDump(CommandContext<CommandSourceStack> ctx) {
        WorldTreeReadOverlay.dumpData();
        ctx.getSource().sendSuccess(() ->
            Component.literal("[KP] TrackGraph data dumped to console log"), false);
        return 1;
    }

    /**
     * {@code /kp debug layer-count}：各图层对象计数。
     */
    private static int debugLayerCount(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() ->
            Component.literal(WorldTreeReadOverlay.getLayerCounts()), false);
        return 1;
    }

    /**
     * {@code /kp debug overlay-anchors}：叠加层锚点诊断。
     *
     * <p>输出相机坐标、缩放比例、屏幕中心、屏幕尺寸、DPR 和维度信息，
     * 用于诊断叠加层对齐问题。
     */
    private static int debugOverlayAnchors(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() ->
            Component.literal(WorldTreeReadOverlay.getOverlayAnchors()), false);
        return 1;
    }
}
