package net.jsmua.kinetic_planner.mapadapter;

import net.jsmua.kinetic_planner.data.ProviderConfig;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;

import javax.annotation.Nullable;

/**
 * Xaero's World Map 的 {@link MapProviderFactory} 实现。
 *
 * <p>将 {@link XaeroMapOverlayProvider} 的创建封装到工厂接口之后。
 * 通过 {@code ModList.get().isLoaded("xaeroworldmap")} 检查可用性。
 *
 * <p>{@link MapProviderFactory} for Xaero's World Map.
 *
 * <p>Wraps {@link XaeroMapOverlayProvider} creation behind the factory interface.
 * Availability is checked via {@code ModList.get().isLoaded("xaeroworldmap")}.
 */
public final class XaeroMapProviderFactory implements MapProviderFactory {

    private Boolean cachedAvailable;

    @Override
    public String modId() {
        return "xaeroworldmap";
    }

    @Override
    public Component displayName() {
        return Component.literal("Xaero's World Map");
    }

    @Override
    public ProviderConfig defaultConfig() {
        return ProviderConfig.defaultValue("xaeroworldmap", "Xaero's World Map");
    }

    @Override
    public boolean isAvailable() {
        if (cachedAvailable == null) {
            cachedAvailable = ModList.get().isLoaded("xaeroworldmap");
        }
        return cachedAvailable;
    }

    @Override
    @Nullable
    public MapOverlayProvider create() {
        if (!isAvailable()) return null;
        return new XaeroMapOverlayProvider();
    }
}
