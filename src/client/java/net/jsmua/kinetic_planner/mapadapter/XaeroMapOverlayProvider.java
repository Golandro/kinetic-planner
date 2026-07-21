package net.jsmua.kinetic_planner.mapadapter;

import net.jsmua.kinetic_planner.KineticPlannerMod;
import net.jsmua.kinetic_planner.mixin.XaeroMapAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
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
 * <h2>已知限制（Phase 0a）</h2>
 * <ul>
 *   <li>维度检测硬编码为 {@code Level.OVERWORLD}，需从 Xaero mapProcessor 获取实际维度</li>
 *   <li>{@code partialTicks} 用 0f 占位，MC 1.21.1 的 {@code Minecraft.getPartialTick()} 已移除</li>
 *   <li>{@code ScreenBase} 在当前 Xaero 版本不存在，简化为直接检查 {@code instanceof GuiMap}</li>
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

            // TODO: 从 Xaero mapProcessor 获取当前维度（当前硬编码 OVERWORLD）
            ResourceKey<Level> dim = Level.OVERWORLD;
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
}
