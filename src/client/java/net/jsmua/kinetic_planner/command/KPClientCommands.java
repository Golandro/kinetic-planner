package net.jsmua.kinetic_planner.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.jsmua.kinetic_planner.config.KPConfig;
import net.jsmua.kinetic_planner.config.OverlayControl;
import net.jsmua.kinetic_planner.config.ProviderConfigControl;
import net.jsmua.kinetic_planner.config.ThemeManager;
import net.jsmua.kinetic_planner.gui.editor.EditorCommands;
import net.jsmua.kinetic_planner.instrument.WorldTreeReadOverlay;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

/**
 * /kp 命令处理器实现（client sourceSet）。
 *
 * <p>实现 {@link KpCommandHandlers} 接口，25 个非 GUI 处理器直接委托
 * {@link OverlayControl} / {@link ThemeManager} / {@link ProviderConfigControl} /
 * {@link WorldTreeReadOverlay}。edit/exit 委托 {@link EditorCommands}。
 *
 * <p>命令树定义在 {@link KPCommandTree}（main sourceSet），
 * 注册由 {@code KineticPlannerClient} 调用 {@code KPCommandTree.register(dispatcher, new KPClientCommands())}。
 */
public final class KPClientCommands implements KpCommandHandlers {

    @Override
    public int overview(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() ->
            Component.literal(OverlayControl.status()), false);
        return 1;
    }

    // === /kp overlay ===

    @Override
    public int overlayToggle(CommandContext<CommandSourceStack> ctx) {
        OverlayControl.toggle();
        ctx.getSource().sendSuccess(() ->
            Component.literal("[KP] Overlay " + (OverlayControl.isEnabled() ? "enabled" : "disabled")), false);
        return 1;
    }

    @Override
    public int overlayEnable(CommandContext<CommandSourceStack> ctx) {
        OverlayControl.enable();
        ctx.getSource().sendSuccess(() ->
            Component.literal("[KP] Overlay enabled"), false);
        return 1;
    }

    @Override
    public int overlayDisable(CommandContext<CommandSourceStack> ctx) {
        OverlayControl.disable();
        ctx.getSource().sendSuccess(() ->
            Component.literal("[KP] Overlay disabled"), false);
        return 1;
    }

    @Override
    public int overlayReload(CommandContext<CommandSourceStack> ctx) {
        OverlayControl.reload();
        ctx.getSource().sendSuccess(() ->
            Component.literal("[KP] Config reloaded and theme rebuilt"), false);
        return 1;
    }

    @Override
    public int overlayStatus(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() ->
            Component.literal(OverlayControl.status()), false);
        return 1;
    }

    @Override
    public int overlayShowCreateToggle(CommandContext<CommandSourceStack> ctx) {
        boolean newVal = !OverlayControl.isShowCreateTrackMap();
        OverlayControl.setShowCreateTrackMap(newVal);
        ctx.getSource().sendSuccess(() ->
            Component.literal("[KP] Show Create Track Map: " + (newVal ? "ON" : "OFF")), false);
        return 1;
    }

    @Override
    public int overlayShowCreateSet(CommandContext<CommandSourceStack> ctx) {
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

    // === /kp provider ===

    @Override
    public int providerList(CommandContext<CommandSourceStack> ctx) {
        String output = ProviderConfigControl.listProviders();
        ctx.getSource().sendSuccess(() ->
            Component.literal(output), false);
        return 1;
    }

    @Override
    public int providerEnable(CommandContext<CommandSourceStack> ctx) {
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

    @Override
    public int providerDisable(CommandContext<CommandSourceStack> ctx) {
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

    @Override
    public int providerSet(CommandContext<CommandSourceStack> ctx) {
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

    @Override
    public int providerGetAll(CommandContext<CommandSourceStack> ctx) {
        String modId = StringArgumentType.getString(ctx, "modId");
        String output = ProviderConfigControl.get(modId, null);
        ctx.getSource().sendSuccess(() -> Component.literal(output), false);
        return 1;
    }

    @Override
    public int providerGetParam(CommandContext<CommandSourceStack> ctx) {
        String modId = StringArgumentType.getString(ctx, "modId");
        String param = StringArgumentType.getString(ctx, "param");
        String output = ProviderConfigControl.get(modId, param);
        ctx.getSource().sendSuccess(() -> Component.literal(output), false);
        return 1;
    }

    @Override
    public int providerReset(CommandContext<CommandSourceStack> ctx) {
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

    @Override
    public int providerResetCircuitAll(CommandContext<CommandSourceStack> ctx) {
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

    @Override
    public int providerResetCircuitOne(CommandContext<CommandSourceStack> ctx) {
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

    // === /kp theme ===

    @Override
    public int themeList(CommandContext<CommandSourceStack> ctx) {
        var themes = ThemeManager.listThemeNames(ThemeManager.getThemesDir());
        String current = KPConfig.getInstance().getActiveThemeName();
        ctx.getSource().sendSuccess(() ->
            Component.literal("[KP] Themes: " + String.join(", ", themes) +
                " (active: " + current + ")"), false);
        return 1;
    }

    @Override
    public int themeSet(CommandContext<CommandSourceStack> ctx) {
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

    @Override
    public int themeReload(CommandContext<CommandSourceStack> ctx) {
        ThemeManager.reload();
        ctx.getSource().sendSuccess(() ->
            Component.literal("[KP] Theme reloaded: " + KPConfig.getInstance().getActiveThemeName()), false);
        return 1;
    }

    @Override
    public int themeReset(CommandContext<CommandSourceStack> ctx) {
        ThemeManager.reset();
        ctx.getSource().sendSuccess(() ->
            Component.literal("[KP] Theme reset to default"), false);
        return 1;
    }

    // === /kp debug ===

    @Override
    public int debugStats(CommandContext<CommandSourceStack> ctx) {
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

    @Override
    public int debugDump(CommandContext<CommandSourceStack> ctx) {
        WorldTreeReadOverlay.dumpData();
        ctx.getSource().sendSuccess(() ->
            Component.literal("[KP] TrackGraph data dumped to console log"), false);
        return 1;
    }

    @Override
    public int debugLayerCount(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() ->
            Component.literal(WorldTreeReadOverlay.getLayerCounts()), false);
        return 1;
    }

    @Override
    public int debugOverlayAnchors(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() ->
            Component.literal(WorldTreeReadOverlay.getOverlayAnchors()), false);
        return 1;
    }

    // === /kp edit / /kp exit -- 委托 EditorCommands ===

    @Override
    public int editMode(CommandContext<CommandSourceStack> ctx) {
        return EditorCommands.editMode(ctx);
    }

    @Override
    public int exitEditMode(CommandContext<CommandSourceStack> ctx) {
        return EditorCommands.exitEditMode(ctx);
    }
}
