package net.jsmua.kinetic_planner.data;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Provider 默认配置注册表。
 *
 * <p>启动时各 {@link net.jsmua.kinetic_planner.mapadapter.MapOverlayProvider}
 * 实现将自己的默认配置注册到此处。{@code KPConfig} 加载 TOML 后按 modId 合并：
 * TOML 值 > Registry 默认值。
 *
 * <p>纯 JVM（main sourceSet），无客户端依赖，可单测。
 */
public final class ProviderConfigRegistry {

    private ProviderConfigRegistry() {}

    /** 按 modId 索引的默认配置。LinkedHashMap 保持注册顺序。 */
    private static final Map<String, ProviderConfig> DEFAULTS = new LinkedHashMap<>();

    /**
     * 注册一个 provider 的默认配置。
     *
     * @param modId         provider mod ID
     * @param defaultConfig 默认配置
     */
    public static void register(String modId, ProviderConfig defaultConfig) {
        DEFAULTS.put(modId, defaultConfig);
    }

    /**
     * 查询某 provider 的默认配置。
     *
     * @param modId provider mod ID
     * @return 默认配置，或 null 如果未注册
     */
    public static ProviderConfig getDefault(String modId) {
        return DEFAULTS.get(modId);
    }

    /**
     * 返回所有已注册 modId 的不可变快照。
     *
     * @return modId 集合（快照，后续注册不影响返回值）
     */
    public static Set<String> getRegisteredModIds() {
        return Set.copyOf(DEFAULTS.keySet());
    }

    /**
     * 清空注册表（仅测试用）。
     */
    static void clear() {
        DEFAULTS.clear();
    }
}
