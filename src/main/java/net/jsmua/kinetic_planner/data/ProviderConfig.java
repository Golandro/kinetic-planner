package net.jsmua.kinetic_planner.data;

/**
 * 地图模组 provider 的配置 record。
 *
 * <p>每个 MapOverlayProvider 实例对应一份 ProviderConfig，存储在
 * {@code config/kineticplanner-client.toml} 的 {@code [provider.<modId>]} 段。
 * 视觉参数（lineWidthScale / alphaScale / dashed）作为全局 Theme 的
 * scale/override，不全量替换 Theme。
 *
 * @param modId          provider 对应的 mod ID（如 "xaeroworldmap"）
 * @param displayName    UI 显示名（如 "Xaero's World Map"）
 * @param enabled        是否启用（false 时 dispatcher 跳过该 provider）
 * @param priority       优先级（数字越小越优先；0 表示最高优先）
 * @param lineWidthScale 线宽缩放因子（1.0 = 用全局 Theme 宽度）
 * @param alphaScale     透明度缩放因子（1.0 = 用全局 Theme alpha）
 * @param dashed         是否改用虚线渲染（覆盖 Theme.track.dashed；P1.1 生效）
 */
public record ProviderConfig(
    String modId,
    String displayName,
    boolean enabled,
    int priority,
    float lineWidthScale,
    float alphaScale,
    boolean dashed
) {
    /**
     * 创建默认配置。
     *
     * @param modId       provider mod ID
     * @param displayName UI 显示名
     * @return 默认配置：enabled=true / priority=0 / scale=1.0 / dashed=false
     */
    public static ProviderConfig defaultValue(String modId, String displayName) {
        return new ProviderConfig(modId, displayName, true, 0, 1.0f, 1.0f, false);
    }

    /** 返回启用状态变更后的新实例（record 不可变）。 */
    public ProviderConfig withEnabled(boolean newEnabled) {
        return new ProviderConfig(modId, displayName, newEnabled, priority,
            lineWidthScale, alphaScale, dashed);
    }

    /** 返回线宽缩放变更后的新实例。 */
    public ProviderConfig withLineWidthScale(float newScale) {
        return new ProviderConfig(modId, displayName, enabled, priority,
            newScale, alphaScale, dashed);
    }

    /** 返回 alpha 缩放变更后的新实例。 */
    public ProviderConfig withAlphaScale(float newScale) {
        return new ProviderConfig(modId, displayName, enabled, priority,
            lineWidthScale, newScale, dashed);
    }

    /** 返回虚线状态变更后的新实例。 */
    public ProviderConfig withDashed(boolean newDashed) {
        return new ProviderConfig(modId, displayName, enabled, priority,
            lineWidthScale, alphaScale, newDashed);
    }
}
