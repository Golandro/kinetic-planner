package net.jsmua.kinetic_planner.mapadapter;

import net.jsmua.kinetic_planner.KineticPlannerMod;
import net.jsmua.kinetic_planner.data.ProviderConfig;
import net.jsmua.kinetic_planner.mixin.XaeroMapAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import xaero.map.gui.GuiMap;

import javax.annotation.Nullable;

/**
 * Xaero's World Map 的 {@link MapOverlayProvider} 实现。
 *
 * <p>通过 {@link XaeroMapAccessor} Mixin accessor 读取 {@code GuiMap} 的内部字段
 * （cameraX、cameraZ、scale），计算 blocksPerPixel 并构建 {@link MapOverlayContext}。
 *
 * <h2>blocksPerPixel 计算公式</h2>
 * <pre>
 *   blocksPerPixel = guiScale * interfaceScale / mapScale
 * </pre>
 * <p>其中：
 * <ul>
 *   <li>{@code guiScale} = screenWidth / guiScaledWidth（MC GUI 缩放因子）</li>
 *   <li>{@code interfaceScale} = windowWidth / screenWidth（Xaero 内部缩放）</li>
 *   <li>{@code mapScale} = Xaero 地图缩放值（acc.kp$scale()）</li>
 * </ul>
 *
 * <h2>已知限制（Phase 0b）</h2>
 * <ul>
 *   <li>维度检测使用玩家当前维度（mc.level.dimension()），Xaero 查看其他维度时不跟随</li>
 *   <li>{@code partialTicks} 用 0f 占位，渲染时由 onMapRender 的 partialTicks 参数提供</li>
 * </ul>
 */
public class XaeroMapOverlayProvider implements MapOverlayProvider {

    @Override
    public boolean isMapOpen(Screen screen) {
        // 直接检查是否为 GuiMap 实例
        // （旧版 Xaero 有 ScreenBase 父类，当前版本 GuiMap 直接继承 Screen）
        return screen instanceof GuiMap;
    }

    @Override
    @Nullable
    public MapOverlayContext captureContext(Screen screen) {
        try {
            GuiMap map = (GuiMap) screen;
            // 通过 Mixin accessor 读取 GuiMap 的 private 字段
            XaeroMapAccessor acc = (XaeroMapAccessor) map;
            double cameraX = acc.kp$cameraX();
            double cameraZ = acc.kp$cameraZ();
            double mapScale = acc.kp$scale();

            Minecraft mc = Minecraft.getInstance();
            int screenWidth = mc.getWindow().getScreenWidth();
            int guiScaledWidth = mc.getWindow().getGuiScaledWidth();
            float dpr = (float) screenWidth / guiScaledWidth;
            double guiScale = (double) screenWidth / mc.getWindow().getGuiScaledWidth();
            double interfaceScale = (double) mc.getWindow().getWidth() / screenWidth;
            double blocksPerPixel = guiScale * interfaceScale / mapScale;

            // 使用玩家当前维度（Xaero 地图通常显示玩家所在维度）
            // 已知限制：Xaero 支持查看其他维度，此简化在 0b MVP 中可接受
            ResourceKey<Level> dim = mc.level != null ? mc.level.dimension() : Level.OVERWORLD;
            return new MapOverlayContext(
                dim, cameraX, cameraZ, blocksPerPixel,
                mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(),
                (int) mc.mouseHandler.xpos(), (int) mc.mouseHandler.ypos(),
                0f, // TODO: MC 1.21.1 getPartialTick() 已移除，需用 DeltaTracker
                dpr
            );
        } catch (Throwable t) {
            KineticPlannerMod.LOGGER.error("XaeroMapOverlayProvider.captureContext failed", t);
            return null;
        }
    }

    @Override
    public String modId() { return "xaeroworldmap"; }

    @Override
    public Component displayName() {
        return Component.literal("Xaero's World Map");
    }

    @Override
    public ProviderConfig defaultConfig() {
        return ProviderConfig.defaultValue("xaeroworldmap", "Xaero's World Map");
    }
}
