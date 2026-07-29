package net.jsmua.kinetic_planner.config;

import net.jsmua.kinetic_planner.data.ProviderConfig;
import net.jsmua.kinetic_planner.data.ProviderConfigBinding;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 基于 {@link ModConfigSpec} value holder 的通用 {@link ProviderConfigBinding} 实现。
 *
 * <p>每个实例服务一个 provider。modId、displayName 和五个
 * {@code ModConfigSpec} value holder 在构造时注入，使该类可复用于任何
 * provider 而无代码重复。
 *
 * <p>替代了原 {@code KPConfig.getProviderConfig} /
 * {@code setProviderEnabled} / {@code setProviderParam} 中的 switch-case 逻辑，
 * 消除了对 per-provider binding 类的需求。
 *
 * <p>Generic {@link ProviderConfigBinding} backed by {@link ModConfigSpec} value holders.
 *
 * <p>A single instance serves one provider. The modId, displayName, and five
 * {@code ModConfigSpec} value holders are injected at construction time,
 * making this class reusable for any provider without code duplication.
 *
 * <p>Replaces the former switch-case logic in
 * {@code KPConfig.getProviderConfig} / {@code setProviderEnabled} / {@code setProviderParam}
 * and eliminates the need for per-provider binding classes.
 */
public final class ModConfigSpecConfigBinding implements ProviderConfigBinding {

    private final String modId;
    private final String displayName;
    private final ModConfigSpec.BooleanValue enabled;
    private final ModConfigSpec.IntValue priority;
    private final ModConfigSpec.DoubleValue lineWidthScale;
    private final ModConfigSpec.DoubleValue alphaScale;
    private final ModConfigSpec.BooleanValue dashed;

    public ModConfigSpecConfigBinding(
            String modId,
            String displayName,
            ModConfigSpec.BooleanValue enabled,
            ModConfigSpec.IntValue priority,
            ModConfigSpec.DoubleValue lineWidthScale,
            ModConfigSpec.DoubleValue alphaScale,
            ModConfigSpec.BooleanValue dashed) {
        this.modId = modId;
        this.displayName = displayName;
        this.enabled = enabled;
        this.priority = priority;
        this.lineWidthScale = lineWidthScale;
        this.alphaScale = alphaScale;
        this.dashed = dashed;
    }

    @Override
    public String modId() {
        return modId;
    }

    @Override
    public ProviderConfig read() {
        return new ProviderConfig(
            modId, displayName,
            enabled.get(),
            priority.get(),
            lineWidthScale.get().floatValue(),
            alphaScale.get().floatValue(),
            dashed.get());
    }

    @Override
    public boolean writeEnabled(boolean value) {
        enabled.set(value);
        return true;
    }

    @Override
    public boolean writeParam(String param, String value) {
        try {
            return switch (param) {
                case "lineWidthScale" -> { lineWidthScale.set(Double.parseDouble(value)); yield true; }
                case "alphaScale" -> { alphaScale.set(Double.parseDouble(value)); yield true; }
                case "dashed" -> {
                    if (!ProviderConfigBinding.isStrictBool(value)) yield false;
                    dashed.set(Boolean.parseBoolean(value));
                    yield true;
                }
                case "priority" -> { priority.set(Integer.parseInt(value)); yield true; }
                default -> false;
            };
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
