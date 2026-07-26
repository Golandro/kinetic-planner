package net.jsmua.kinetic_planner.config;

import net.jsmua.kinetic_planner.KineticPlannerClient;
import net.jsmua.kinetic_planner.instrument.WorldTreeReadOverlay;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.network.chat.Component;

/**
 * /kp 命令体系注册（P0 + P1.0 Phase A 完整版）。
 *
 * <p>通过 {@link net.neoforged.neoforge.client.event.RegisterClientCommandsEvent}
 * 注册为客户端命令。命令逻辑委托 {@link OverlayControl} / {@link ThemeManager} /
 * {@link ProviderConfigControl} / {@link WorldTreeReadOverlay}，此类仅负责命令分发。
 *
 * <h2>命令树</h2>
 * <pre>
 * /kp                         -- 综合概览
 * /kp overlay toggle          -- 切换叠加开关
 * /kp overlay enable          -- 显式开启
 * /kp overlay disable         -- 显式关闭
 * /kp overlay reload          -- 从配置值重建 Theme 并应用
 * /kp overlay status          -- 查看状态（开关/图数/节点数/边数）
 * /kp overlay show-create [true|false] -- 切换/设置显示 Create 列车地图叠加层
 * /kp provider list           -- 列出所有 provider 及状态
 * /kp provider enable &lt;modId&gt; -- 启用某 provider
 * /kp provider disable &lt;modId&gt;-- 禁用某 provider
 * /kp provider set &lt;modId&gt; &lt;param&gt; &lt;value&gt; -- 设置 provider 参数
 * /kp provider get &lt;modId&gt; [param] -- 查询 provider 配置
 * /kp provider reset &lt;modId&gt;  -- 重置 provider 为默认
 * /kp provider reset-circuit [modId] -- 重置熔断状态（省略 modId 重置全部）
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
                    .executes(KPCommands::overlayStatus))
                .then(Commands.literal("show-create")
                    .executes(KPCommands::overlayShowCreateToggle)
                    .then(Commands.argument("value", StringArgumentType.word())
                        .executes(KPCommands::overlayShowCreateSet))))
            .then(Commands.literal("provider")
                .then(Commands.literal("list")
                    .executes(KPCommands::providerList))
                .then(Commands.literal("enable")
                    .then(Commands.argument("modId", StringArgumentType.word())
                        .executes(KPCommands::providerEnable)))
                .then(Commands.literal("disable")
                    .then(Commands.argument("modId", StringArgumentType.word())
                        .executes(KPCommands::providerDisable)))
                .then(Commands.literal("set")
                    .then(Commands.argument("modId", StringArgumentType.word())
                        .then(Commands.argument("param", StringArgumentType.word())
                            .then(Commands.argument("value", StringArgumentType.word())
                                .executes(KPCommands::providerSet)))))
                .then(Commands.literal("get")
                    .then(Commands.argument("modId", StringArgumentType.word())
                        .executes(KPCommands::providerGetAll)
                        .then(Commands.argument("param", StringArgumentType.word())
                            .executes(KPCommands::providerGetParam))))
                .then(Commands.literal("reset")
                    .then(Commands.argument("modId", StringArgumentType.word())
                        .executes(KPCommands::providerReset)))
                .then(Commands.literal("reset-circuit")
                    .executes(KPCommands::providerResetCircuitAll)
                    .then(Commands.argument("modId", StringArgumentType.word())
                        .executes(KPCommands::providerResetCircuitOne))))
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

    // === /kp overlay show-create -- 显示/隐藏 Create 列车地图叠加层 ===

    /**
     * {@code /kp overlay show-create}：切换显示 Create 列车地图叠加层。
     *
     * <p>当此开关为 true 时，CreateTrainMapMixin 放行 Create 的 renderAndPick，
     * Create 的列车地图叠加层在地图上正常渲染；为 false 时由 KP 完全替代。
     */
    private static int overlayShowCreateToggle(CommandContext<CommandSourceStack> ctx) {
        boolean newVal = !OverlayControl.isShowCreateTrackMap();
        OverlayControl.setShowCreateTrackMap(newVal);
        ctx.getSource().sendSuccess(() ->
            Component.literal("[KP] Show Create Track Map: " + (newVal ? "ON" : "OFF")), false);
        return 1;
    }

    /**
     * {@code /kp overlay show-create <value>}：显式设置显示 Create 列车地图叠加层。
     *
     * <p>{@code value} 接受 {@code true}/{@code false}（{@link Boolean#parseBoolean} 解析）。
     */
    private static int overlayShowCreateSet(CommandContext<CommandSourceStack> ctx) {
        String value = StringArgumentType.getString(ctx, "value");
        if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
            ctx.getSource().sendFailure(
                Component.literal("[KP] Invalid value: " + value + " (use true or false)"));
            return 0;
        }
        boolean show = Boolean.parseBoolean(value);
        OverlayControl.setShowCreateTrackMap(show);
        ctx.getSource().sendSuccess(() ->
            Component.literal("[KP] Show Create Track Map: " + (show ? "ON" : "OFF")), false);
        return 1;
    }

    // === /kp provider -- 地图模组 provider 配置 ===

    /**
     * {@code /kp provider list}：列出所有已注册 provider 及其状态、参数。
     *
     * <p>当前激活的 provider 以 {@code *} 标记。
     */
    private static int providerList(CommandContext<CommandSourceStack> ctx) {
        String output = ProviderConfigControl.listProviders();
        ctx.getSource().sendSuccess(() ->
            Component.literal(output), false);
        return 1;
    }

    /**
     * {@code /kp provider enable <modId>}：启用指定 provider。
     */
    private static int providerEnable(CommandContext<CommandSourceStack> ctx) {
        String modId = StringArgumentType.getString(ctx, "modId");
        if (ProviderConfigControl.enable(modId)) {
            ctx.getSource().sendSuccess(() ->
                Component.literal("[KP] Provider " + modId + " enabled"), false);
            return 1;
        } else {
            ctx.getSource().sendFailure(
                Component.literal("[KP] Unknown provider: " + modId +
                    ". Use /kp provider list to see available providers."));
            return 0;
        }
    }

    /**
     * {@code /kp provider disable <modId>}：禁用指定 provider。
     */
    private static int providerDisable(CommandContext<CommandSourceStack> ctx) {
        String modId = StringArgumentType.getString(ctx, "modId");
        if (ProviderConfigControl.disable(modId)) {
            ctx.getSource().sendSuccess(() ->
                Component.literal("[KP] Provider " + modId + " disabled"), false);
            return 1;
        } else {
            ctx.getSource().sendFailure(
                Component.literal("[KP] Unknown provider: " + modId));
            return 0;
        }
    }

    /**
     * {@code /kp provider set <modId> <param> <value>}：设置 provider 视觉参数。
     *
     * <p>{@code param} 支持 {@code lineWidthScale}/{@code alphaScale}/{@code dashed}/{@code priority}。
     */
    private static int providerSet(CommandContext<CommandSourceStack> ctx) {
        String modId = StringArgumentType.getString(ctx, "modId");
        String param = StringArgumentType.getString(ctx, "param");
        String value = StringArgumentType.getString(ctx, "value");
        if (ProviderConfigControl.setParam(modId, param, value)) {
            ctx.getSource().sendSuccess(() ->
                Component.literal("[KP] " + modId + "." + param + " = " + value), false);
            return 1;
        } else {
            ctx.getSource().sendFailure(
                Component.literal("[KP] Failed to set " + modId + "." + param +
                    " = " + value + " (unknown provider/param or invalid value)"));
            return 0;
        }
    }

    /**
     * {@code /kp provider get <modId>}：查询 provider 的全部配置。
     */
    private static int providerGetAll(CommandContext<CommandSourceStack> ctx) {
        String modId = StringArgumentType.getString(ctx, "modId");
        String output = ProviderConfigControl.get(modId, null);
        ctx.getSource().sendSuccess(() -> Component.literal(output), false);
        return 1;
    }

    /**
     * {@code /kp provider get <modId> <param>}：查询单个参数值。
     */
    private static int providerGetParam(CommandContext<CommandSourceStack> ctx) {
        String modId = StringArgumentType.getString(ctx, "modId");
        String param = StringArgumentType.getString(ctx, "param");
        String output = ProviderConfigControl.get(modId, param);
        ctx.getSource().sendSuccess(() -> Component.literal(output), false);
        return 1;
    }

    /**
     * {@code /kp provider reset <modId>}：重置 provider 配置为默认值。
     */
    private static int providerReset(CommandContext<CommandSourceStack> ctx) {
        String modId = StringArgumentType.getString(ctx, "modId");
        if (ProviderConfigControl.reset(modId)) {
            ctx.getSource().sendSuccess(() ->
                Component.literal("[KP] Provider " + modId + " reset to default"), false);
            return 1;
        } else {
            ctx.getSource().sendFailure(
                Component.literal("[KP] Unknown provider: " + modId));
            return 0;
        }
    }

    /**
     * {@code /kp provider reset-circuit}：重置所有 provider 的熔断状态。
     */
    private static int providerResetCircuitAll(CommandContext<CommandSourceStack> ctx) {
        boolean hadAny = ProviderConfigControl.resetCircuit(null);
        if (hadAny) {
            ctx.getSource().sendSuccess(() ->
                Component.literal("[KP] All circuit breakers reset"), false);
        } else {
            ctx.getSource().sendSuccess(() ->
                Component.literal("[KP] No circuit breakers to reset"), false);
        }
        return 1;
    }

    /**
     * {@code /kp provider reset-circuit <modId>}：重置指定 provider 的熔断状态。
     */
    private static int providerResetCircuitOne(CommandContext<CommandSourceStack> ctx) {
        String modId = StringArgumentType.getString(ctx, "modId");
        if (ProviderConfigControl.resetCircuit(modId)) {
            ctx.getSource().sendSuccess(() ->
                Component.literal("[KP] Circuit breaker reset for " + modId), false);
            return 1;
        } else {
            ctx.getSource().sendSuccess(() ->
                Component.literal("[KP] No circuit breaker for " + modId), false);
            return 0;
        }
    }
}
