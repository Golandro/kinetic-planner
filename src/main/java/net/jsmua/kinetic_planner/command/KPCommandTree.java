package net.jsmua.kinetic_planner.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/**
 * /kp 命令树构建器 - 纯定义层，无 GUI 依赖。
 *
 * <p>定义在 main (common) sourceSet，仅依赖 Brigadier + MC command API
 * （{@code CommandSourceStack}、{@code Commands} 均非 {@code client.*} 包）。
 * 具体处理器实现由 client sourceSet 的 {@code KPClientCommands} 提供。
 *
 * <h2>命令树</h2>
 * <pre>
 * /kp                         -- 综合概览
 * /kp overlay toggle          -- 切换叠加开关
 * /kp overlay enable          -- 显式开启
 * /kp overlay disable         -- 显式关闭
 * /kp overlay reload          -- 从配置值重建 Theme 并应用
 * /kp overlay status          -- 查看状态
 * /kp overlay show-create [true|false] -- 切换/设置显示 Create 列车地图叠加层
 * /kp provider list           -- 列出所有 provider 及状态
 * /kp provider enable &lt;modId&gt; -- 启用某 provider
 * /kp provider disable &lt;modId&gt;-- 禁用某 provider
 * /kp provider set &lt;modId&gt; &lt;param&gt; &lt;value&gt; -- 设置 provider 参数
 * /kp provider get &lt;modId&gt; [param] -- 查询 provider 配置
 * /kp provider reset &lt;modId&gt;  -- 重置 provider 为默认
 * /kp provider reset-circuit [modId] -- 重置熔断状态
 * /kp theme list              -- 扫描目录列出可用主题
 * /kp theme set &lt;name&gt;        -- 切换到指定主题
 * /kp theme reload            -- 从磁盘重载当前主题 JSON
 * /kp theme reset             -- 重置为默认主题
 * /kp debug stats             -- 渲染统计
 * /kp debug dump              -- 转储 TrackGraph 数据到日志
 * /kp debug layer-count       -- 各图层对象计数
 * /kp debug overlay-anchors   -- 叠加层锚点诊断
 * /kp edit                    -- 进入编辑模式
 * /kp exit                    -- 退出编辑模式
 * </pre>
 */
public final class KPCommandTree {

    private KPCommandTree() {}

    /**
     * 注册 /kp 命令树。
     *
     * @param dispatcher 客户端命令调度器
     * @param handlers   命令处理器实现（由 client sourceSet 提供）
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
                                 KpCommandHandlers handlers) {
        dispatcher.register(Commands.literal("kp")
            .executes(handlers::overview)
            .then(Commands.literal("overlay")
                .then(Commands.literal("toggle")
                    .executes(handlers::overlayToggle))
                .then(Commands.literal("enable")
                    .executes(handlers::overlayEnable))
                .then(Commands.literal("disable")
                    .executes(handlers::overlayDisable))
                .then(Commands.literal("reload")
                    .executes(handlers::overlayReload))
                .then(Commands.literal("status")
                    .executes(handlers::overlayStatus))
                .then(Commands.literal("show-create")
                    .executes(handlers::overlayShowCreateToggle)
                    .then(Commands.argument("value", StringArgumentType.word())
                        .executes(handlers::overlayShowCreateSet))))
            .then(Commands.literal("provider")
                .then(Commands.literal("list")
                    .executes(handlers::providerList))
                .then(Commands.literal("enable")
                    .then(Commands.argument("modId", StringArgumentType.word())
                        .executes(handlers::providerEnable)))
                .then(Commands.literal("disable")
                    .then(Commands.argument("modId", StringArgumentType.word())
                        .executes(handlers::providerDisable)))
                .then(Commands.literal("set")
                    .then(Commands.argument("modId", StringArgumentType.word())
                        .then(Commands.argument("param", StringArgumentType.word())
                            .then(Commands.argument("value", StringArgumentType.word())
                                .executes(handlers::providerSet)))))
                .then(Commands.literal("get")
                    .then(Commands.argument("modId", StringArgumentType.word())
                        .executes(handlers::providerGetAll)
                        .then(Commands.argument("param", StringArgumentType.word())
                            .executes(handlers::providerGetParam))))
                .then(Commands.literal("reset")
                    .then(Commands.argument("modId", StringArgumentType.word())
                        .executes(handlers::providerReset)))
                .then(Commands.literal("reset-circuit")
                    .executes(handlers::providerResetCircuitAll)
                    .then(Commands.argument("modId", StringArgumentType.word())
                        .executes(handlers::providerResetCircuitOne))))
            .then(Commands.literal("theme")
                .then(Commands.literal("list")
                    .executes(handlers::themeList))
                .then(Commands.literal("set")
                    .then(Commands.argument("name", StringArgumentType.word())
                        .executes(handlers::themeSet)))
                .then(Commands.literal("reload")
                    .executes(handlers::themeReload))
                .then(Commands.literal("reset")
                    .executes(handlers::themeReset)))
            .then(Commands.literal("debug")
                .then(Commands.literal("stats")
                    .executes(handlers::debugStats))
                .then(Commands.literal("dump")
                    .executes(handlers::debugDump))
                .then(Commands.literal("layer-count")
                    .executes(handlers::debugLayerCount))
                .then(Commands.literal("overlay-anchors")
                    .executes(handlers::debugOverlayAnchors)))
            .then(Commands.literal("edit")
                .executes(handlers::editMode))
            .then(Commands.literal("exit")
                .executes(handlers::exitEditMode))
        );
    }
}
