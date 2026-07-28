package net.jsmua.kinetic_planner.config;

import net.jsmua.kinetic_planner.data.ProviderConfig;
import net.jsmua.kinetic_planner.data.ProviderConfigRegistry;
import net.jsmua.kinetic_planner.mapadapter.MapOverlayDispatcher;
import net.jsmua.kinetic_planner.mapadapter.MapOverlayProvider;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Provider 配置状态管理器。
 *
 * <p>封装 per-provider 配置的 CRUD 操作，命令层（{@link net.jsmua.kinetic_planner.command.KPCommands}）通过此类操作配置，
 * 不直接访问 {@link KPConfig} 或 {@link net.jsmua.kinetic_planner.instrument.WorldTreeReadOverlay}。
 *
 * <p>修改配置后自动通过 {@link OverlayControl#reload()} 重建 Theme 并应用到渲染层。
 */
public final class ProviderConfigControl {

    private static final IKPConfig config = KPConfig.getInstance();

    private ProviderConfigControl() {}

    /**
     * 列出所有已注册 provider 及其状态。
     *
     * <p>格式（每行一个 provider，{@code *} 标记当前激活的 provider，{@code [FUSED]} 标记已熔断的）：
     * <pre>
     * [KP] Map Providers:
     *   * xaeroworldmap  [ON]  pri=0 lineWidth=1.00 alpha=1.00 dashed=false
     *     journeymap     [OFF] pri=1 lineWidth=1.00 alpha=1.00 dashed=false [FUSED]
     * </pre>
     *
     * @return 格式化字符串，每行一个 provider
     */
    public static String listProviders() {
        Optional<String> active = MapOverlayDispatcher.activeProviderModId();
        List<String> lines = new ArrayList<>();
        lines.add("[KP] Map Providers:");
        for (MapOverlayProvider p : MapOverlayDispatcher.registeredProviders()) {
            ProviderConfig pc = config.getProviderConfig(p.modId());
            String status = pc != null && pc.enabled() ? "ON" : "OFF";
            String activeMark = active.map(a -> a.equals(p.modId()) ? " *" : "  ").orElse("  ");
            String fusedMark = MapOverlayDispatcher.isCircuitBroken(p.modId()) ? " [FUSED]" : "";
            lines.add(String.format("%s %-15s [%s] pri=%d lineWidth=%.2f alpha=%.2f dashed=%s%s",
                activeMark, p.modId(), status,
                pc != null ? pc.priority() : 0,
                pc != null ? pc.lineWidthScale() : 1.0f,
                pc != null ? pc.alphaScale() : 1.0f,
                pc != null && pc.dashed() ? "true" : "false",
                fusedMark));
        }
        return String.join("\n", lines);
    }

    /**
     * 启用指定 provider。
     *
     * @param modId provider mod ID
     * @return {@code true} 如果设置成功（modId 已知且配置写入成功）
     */
    public static boolean enable(String modId) {
        if (!isKnownModId(modId)) return false;
        boolean ok = config.setProviderEnabled(modId, true);
        if (ok) OverlayControl.reload();
        return ok;
    }

    /**
     * 禁用指定 provider。
     *
     * @param modId provider mod ID
     * @return {@code true} 如果设置成功
     */
    public static boolean disable(String modId) {
        if (!isKnownModId(modId)) return false;
        boolean ok = config.setProviderEnabled(modId, false);
        if (ok) OverlayControl.reload();
        return ok;
    }

    /**
     * 设置 provider 的视觉参数。
     *
     * @param modId  provider mod ID
     * @param param  参数名（{@code lineWidthScale} / {@code alphaScale} / {@code dashed} / {@code priority}）
     * @param value  字符串形式的新值
     * @return {@code true} 如果设置成功（modId 已知 + 参数名合法 + 值合法）
     */
    public static boolean setParam(String modId, String param, String value) {
        if (!isKnownModId(modId)) return false;
        boolean ok = config.setProviderParam(modId, param, value);
        if (ok) OverlayControl.reload();
        return ok;
    }

    /**
     * 查询 provider 的配置或单个参数。
     *
     * @param modId provider mod ID
     * @param param 参数名（{@code null} 或空字符串表示查询全部）
     * @return 格式化字符串；未知 modId 返回错误提示
     */
    public static String get(String modId, String param) {
        ProviderConfig pc = config.getProviderConfig(modId);
        if (pc == null) {
            return "[KP] Unknown provider: " + modId;
        }
        if (param == null || param.isEmpty()) {
            return String.format("[KP] %s | enabled=%s | priority=%d | lineWidth=%.2f | alpha=%.2f | dashed=%s",
                modId, pc.enabled(), pc.priority(),
                pc.lineWidthScale(), pc.alphaScale(), pc.dashed());
        }
        return switch (param) {
            case "enabled" -> "[KP] " + modId + ".enabled = " + pc.enabled();
            case "priority" -> "[KP] " + modId + ".priority = " + pc.priority();
            case "lineWidthScale" -> "[KP] " + modId + ".lineWidthScale = " + pc.lineWidthScale();
            case "alphaScale" -> "[KP] " + modId + ".alphaScale = " + pc.alphaScale();
            case "dashed" -> "[KP] " + modId + ".dashed = " + pc.dashed();
            default -> "[KP] Unknown param: " + param + " (valid: enabled/priority/lineWidthScale/alphaScale/dashed)";
        };
    }

    /**
     * 重置 provider 配置为默认值。
     *
     * <p>从 {@link ProviderConfigRegistry} 取默认值，逐项写回 KPConfig。
     *
     * @param modId provider mod ID
     * @return {@code true} 如果重置成功（modId 已注册）
     */
    public static boolean reset(String modId) {
        ProviderConfig def = ProviderConfigRegistry.getDefault(modId);
        if (def == null) return false;
        boolean ok = config.setProviderEnabled(modId, def.enabled())
            && config.setProviderParam(modId, "priority", String.valueOf(def.priority()))
            && config.setProviderParam(modId, "lineWidthScale", String.valueOf(def.lineWidthScale()))
            && config.setProviderParam(modId, "alphaScale", String.valueOf(def.alphaScale()))
            && config.setProviderParam(modId, "dashed", String.valueOf(def.dashed()));
        if (ok) OverlayControl.reload();
        return ok;
    }

    /**
     * 重置 provider 的熔断状态。
     *
     * @param modId provider mod ID；null 表示重置全部
     * @return true 如果有熔断被重置
     */
    public static boolean resetCircuit(@Nullable String modId) {
        var failed = MapOverlayDispatcher.getFailedProviders();
        if (modId != null) {
            boolean wasBroken = failed.contains(modId);
            MapOverlayDispatcher.resetCircuitBreaker(modId);
            return wasBroken;
        } else {
            boolean hadAny = !failed.isEmpty();
            MapOverlayDispatcher.resetCircuitBreaker(null);
            return hadAny;
        }
    }

    /**
     * 检查 modId 是否为已注册的 provider。
     */
    private static boolean isKnownModId(String modId) {
        return MapOverlayDispatcher.registeredProviders().stream()
            .anyMatch(p -> p.modId().equals(modId));
    }
}
