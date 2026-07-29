package net.jsmua.kinetic_planner.mapadapter;

import net.jsmua.kinetic_planner.data.ProviderConfig;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;

import javax.annotation.Nullable;

/**
 * JourneyMap 的 {@link MapProviderFactory} 实现。
 *
 * <p>将 {@link JourneyMapOverlayProvider} 的创建封装到工厂接口之后。
 * 通过 {@code ModList.get().isLoaded("journeymap")} 检查可用性。
 *
 * <p>{@link MapProviderFactory} for JourneyMap.
 *
 * <p>Wraps {@link JourneyMapOverlayProvider} creation behind the factory interface.
 * Availability is checked via {@code ModList.get().isLoaded("journeymap")}.
 */
public final class JourneyMapMapProviderFactory implements MapProviderFactory {

    private Boolean cachedAvailable;

    @Override
    public String modId() {
        return "journeymap";
    }

    @Override
    public Component displayName() {
        return Component.literal("JourneyMap");
    }

    @Override
    public ProviderConfig defaultConfig() {
        return ProviderConfig.defaultValue("journeymap", "JourneyMap");
    }

    @Override
    public boolean isAvailable() {
        if (cachedAvailable == null) {
            cachedAvailable = ModList.get().isLoaded("journeymap");
        }
        return cachedAvailable;
    }

    @Override
    @Nullable
    public MapOverlayProvider create() {
        if (!isAvailable()) return null;
        return new JourneyMapOverlayProvider();
    }
}
