package net.jsmua.kinetic_planner.mapadapter;

import net.jsmua.kinetic_planner.registry.KPId;
import net.jsmua.kinetic_planner.registry.KPRegistry;

import java.util.List;

/**
 * 客户端 {@link MapProviderFactory} 注册表。
 *
 * <p>基于通用 {@link KPRegistry} 构建。工厂在
 * {@code FMLClientSetupEvent} 期间注册，首次使用前冻结。
 *
 * <p>第三方模组在 client setup 阶段（冻结前）通过调用
 * {@link #register} 注册自己的工厂。
 *
 * <p>Client-side registry for {@link MapProviderFactory} instances.
 *
 * <p>Built on the generic {@link KPRegistry}. Factories are registered during
 * {@code FMLClientSetupEvent} and frozen before first use.
 *
 * <p>Third-party mods register their factories by calling
 * {@link #register} during client setup (before freeze).
 */
public final class MapProviderRegistry {

    private MapProviderRegistry() {}

    private static final KPRegistry<MapProviderFactory> REGISTRY = new KPRegistry<>();

    /**
     * 注册一个地图 provider 工厂。
     *
     * @param factory 待注册的工厂 / the factory to register
     * @throws IllegalStateException 如果注册表已冻结或 modId 重复
     *         / if registry is frozen or duplicate modId
     */
    public static void register(MapProviderFactory factory) {
        REGISTRY.register(KPId.kp(factory.modId()), factory);
    }

    /**
     * 返回所有已注册工厂（不可修改）。
     *
     * @return 按注册顺序排列的所有工厂集合 / collection of all factories in registration order
     */
    public static List<MapProviderFactory> all() {
        return List.copyOf(REGISTRY.all());
    }

    /**
     * 仅返回目标模组已加载的工厂。
     *
     * @return 可用工厂列表 / list of available factories
     */
    public static List<MapProviderFactory> available() {
        return REGISTRY.all().stream()
            .filter(MapProviderFactory::isAvailable)
            .toList();
    }

    /**
     * 冻结注册表。在 client setup 完成后调用。
     * / Freeze the registry. Called after client setup is complete.
     */
    public static void freeze() {
        REGISTRY.freeze();
    }

    /**
     * 返回 true 如果注册表已冻结。
     * / Return true if registry is frozen.
     */
    public static boolean isFrozen() {
        return REGISTRY.isFrozen();
    }
}
