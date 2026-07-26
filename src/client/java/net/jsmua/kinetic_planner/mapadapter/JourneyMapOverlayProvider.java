package net.jsmua.kinetic_planner.mapadapter;

import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.display.Context;
import journeymap.api.v2.client.util.UIState;
import net.jsmua.kinetic_planner.KineticPlannerMod;
import net.jsmua.kinetic_planner.data.ProviderConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;
import javax.annotation.Nullable;

/**
 * JourneyMap 的 {@link MapOverlayProvider} 实现。
 *
 * <p>通过 JM 官方 API（{@link IClientAPI#getUIState}）检测全屏地图状态并捕获上下文。
 * 无需 Mixin。
 *
 * <h2>JM API 字段映射</h2>
 * <ul>
 *   <li>{@code state.active} -> 是否全屏地图打开</li>
 *   <li>{@code state.mapCenter} (BlockPos) -> cameraBlockX / cameraBlockZ</li>
 *   <li>{@code 1 / state.blockSize} -> blocksPerPixel（blockSize = zoom/512，即每方块像素数）</li>
 *   <li>{@code state.dimension} -> 维度</li>
 * </ul>
 *
 * <h2>软依赖守卫</h2>
 * <p>所有 JM API 类型引用在 {@link #isJourneyMapLoaded()} 守卫之后。
 * JM 未安装时 {@code isJourneyMapLoaded()} 返回 false，不触发 JM API 类加载。
 */
public class JourneyMapOverlayProvider implements MapOverlayProvider {

    private static Boolean jmLoaded;

    /**
     * 检查 JourneyMap 是否已安装。
     * 缓存结果避免每 tick 重复查询。
     */
    private static boolean isJourneyMapLoaded() {
        if (jmLoaded == null) {
            jmLoaded = ModList.get().isLoaded("journeymap");
        }
        return jmLoaded;
    }

    @Override
    public boolean isMapOpen(Screen screen) {
        if (!isJourneyMapLoaded()) return false;
        return jmIsMapOpen();
    }

    private boolean jmIsMapOpen() {
        try {
            IClientAPI api = KineticPlannerJMPlugin.getApi();
            if (api == null) return false;
            UIState state = api.getUIState(Context.UI.Fullscreen);
            return state != null && state.active;
        } catch (Throwable t) {
            KineticPlannerMod.LOGGER.error("JM isMapOpen failed", t);
            return false;
        }
    }

    @Override
    @Nullable
    public MapOverlayContext captureContext(Screen screen) {
        if (!isJourneyMapLoaded()) return null;
        return jmCaptureContext();
    }

    @Nullable
    private MapOverlayContext jmCaptureContext() {
        try {
            IClientAPI api = KineticPlannerJMPlugin.getApi();
            if (api == null) return null;
            UIState state = api.getUIState(Context.UI.Fullscreen);
            if (state == null || !state.active) return null;

            Minecraft mc = Minecraft.getInstance();
            int screenWidth = mc.getWindow().getGuiScaledWidth();
            int screenHeight = mc.getWindow().getGuiScaledHeight();
            float dpr = (float) mc.getWindow().getScreenWidth() / screenWidth;

            // blockSize = zoom / 512.0，表示一个方块在屏幕上的像素宽度
            // blocksPerPixel = 1 / blockSize（每像素代表多少方块）
            double blocksPerPixel = state.blockSize > 0 ? 1.0 / state.blockSize : 1.0;

            return new MapOverlayContext(
                state.dimension,
                state.mapCenter != null ? state.mapCenter.getX() : 0,
                state.mapCenter != null ? state.mapCenter.getZ() : 0,
                blocksPerPixel,
                screenWidth, screenHeight,
                (int) mc.mouseHandler.xpos(), (int) mc.mouseHandler.ypos(),
                0f,
                dpr
            );
        } catch (Throwable t) {
            KineticPlannerMod.LOGGER.error("JM captureContext failed", t);
            return null;
        }
    }

    @Override
    public String modId() { return "journeymap"; }

    @Override
    public Component displayName() {
        return Component.literal("JourneyMap");
    }

    @Override
    public ProviderConfig defaultConfig() {
        return ProviderConfig.defaultValue("journeymap", "JourneyMap");
    }
}
