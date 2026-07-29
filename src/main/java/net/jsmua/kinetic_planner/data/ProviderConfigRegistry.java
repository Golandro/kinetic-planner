package net.jsmua.kinetic_planner.data;

import net.jsmua.kinetic_planner.registry.KPId;
import net.jsmua.kinetic_planner.registry.KPRegistry;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Provider 默认配置注册表。
 *
 * <p>启动时各 {@link net.jsmua.kinetic_planner.mapadapter.MapOverlayProvider}
 * 实现将自己的默认配置注册到此处。{@code KPConfig} 加载 TOML 后按 modId 合并：
 * TOML 值 > Registry 默认值。
 *
 * <p>纯 JVM（main sourceSet），无客户端依赖，可单测。
 * 内部使用 {@link KPRegistry} 统一注册表模式。
 *
 * <p><b>注意：</b>内部 {@link KPRegistry} 实例不冻结（provider 默认配置可能在
 * 运行时通过命令动态注册）。{@link #clear()} 通过重建实例实现重置。
 *
 * <p>Provider default config registry.
 *
 * <p>At startup, each {@link net.jsmua.kinetic_planner.mapadapter.MapOverlayProvider}
 * implementation registers its default config here. After {@code KPConfig} loads TOML,
 * values are merged by modId: TOML value > Registry default.
 *
 * <p>Pure JVM (main sourceSet), no client deps, unit-testable.
 * Uses {@link KPRegistry} internally to unify the registry pattern.
 *
 * <p><b>Note:</b> The internal {@link KPRegistry} instance is NOT frozen (provider
 * default configs may be registered dynamically at runtime via commands).
 * {@link #clear()} resets by recreating the instance.
 */
public final class ProviderConfigRegistry {

    private ProviderConfigRegistry() {}

    private static KPRegistry<ProviderConfig> REGISTRY = new KPRegistry<>();

    /**
     * 注册一个 provider 的默认配置。
     *
     * @param modId         provider mod ID
     * @param defaultConfig 默认配置 / default config
     */
    public static void register(String modId, ProviderConfig defaultConfig) {
        REGISTRY.register(KPId.kp(modId), defaultConfig);
    }

    /**
     * 查询某 provider 的默认配置。
     *
     * @param modId provider mod ID
     * @return 默认配置，或 null 如果未注册
     *         / default config, or null if not registered
     */
    public static ProviderConfig getDefault(String modId) {
        return REGISTRY.get(KPId.kp(modId)).orElse(null);
    }

    /**
     * 返回所有已注册 modId 的不可变快照。
     *
     * @return modId 集合（快照，后续注册不影响返回值）
     *         / modId set (snapshot; subsequent registrations do not affect returned value)
     */
    public static Set<String> getRegisteredModIds() {
        return REGISTRY.ids().stream()
            .map(KPId::path)
            .collect(Collectors.toSet());
    }

    /**
     * 清空注册表（仅测试用）。
     *
     * <p>由于 {@link KPRegistry} 不支持清空，此处通过重建实例实现。
     *
     * <p>Clear the registry (test only).
     *
     * <p>Since {@link KPRegistry} does not support clearing, this recreates the instance.
     */
    static void clear() {
        REGISTRY = new KPRegistry<>();
    }
}
