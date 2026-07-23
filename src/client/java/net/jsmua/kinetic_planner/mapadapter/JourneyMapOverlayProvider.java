package net.jsmua.kinetic_planner.mapadapter;

import net.jsmua.kinetic_planner.data.ProviderConfig;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import javax.annotation.Nullable;

/**
 * JourneyMap 的 {@link MapOverlayProvider} 占位实现（Phase 0.5 激活）。
 *
 * <p>Phase 0a 阶段所有方法返回空值/false，不影响 {@link MapOverlayDispatcher} 轮询。
 * Phase 0.5 将实现 JourneyMap 全屏地图的上下文捕获，校准 {@link MapOverlayProvider} 接口的可移植性。
 */
public class JourneyMapOverlayProvider implements MapOverlayProvider {

    /** {@inheritDoc} Phase 0a 恒返回 false。 */
    @Override
    public boolean isMapOpen(Screen screen) { return false; }

    /** {@inheritDoc} Phase 0a 恒返回 null。 */
    @Override
    @Nullable
    public MapOverlayContext captureContext(Screen screen) { return null; }

    @Override
    public String modId() { return "journeymap"; }

    @Override
    public Component displayName() {
        return Component.literal("JourneyMap");
    }

    @Override
    public ProviderConfig defaultConfig() {
        // JM Mixin 推迟，但 defaultConfig 仍注册以便出现在 CLI/UI
        return ProviderConfig.defaultValue("journeymap", "JourneyMap");
    }
}
