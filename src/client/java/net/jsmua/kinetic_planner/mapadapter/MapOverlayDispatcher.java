package net.jsmua.kinetic_planner.mapadapter;

import net.jsmua.kinetic_planner.KineticPlannerMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.*;

/**
 * 地图叠加调度器，每 tick 轮询已注册的 {@link MapOverlayProvider} 并管理熔断。
 *
 * <p>调度流程（每客户端 tick 执行一次）：
 * <ol>
 *   <li>获取当前 {@code Minecraft.getInstance().screen}</li>
 *   <li>若屏幕为 null，清除上下文并返回</li>
 *   <li>按注册顺序遍历 provider，跳过已熔断的</li>
 *   <li>若 {@link MapOverlayProvider#isMapOpen} 返回 true，调用 {@link MapOverlayProvider#captureContext}</li>
 *   <li>捕获成功则暂存上下文并停止轮询；捕获失败则熔断该 provider（不再重试）</li>
 * </ol>
 *
 * <h2>熔断机制</h2>
 * <p>当某个 provider 在 {@code isMapOpen} 或 {@code captureContext} 中抛出异常时，
 * 其 modId 被加入 {@link #FAILED} 集合，后续 tick 不再调用该 provider。
 * 这防止单个地图模组的 bug 导致每 tick 异常刷屏。
 *
 * <p>Phase 0.5 可通过命令或配置重置熔断状态，支持用户更换地图模组后无需重启。
 */
public class MapOverlayDispatcher {

    /** 已注册的地图 provider 列表，按优先级排序。 */
    private static final List<MapOverlayProvider> PROVIDERS = new ArrayList<>();

    /** 已熔断的 provider modId 集合，后续 tick 跳过这些 provider。 */
    private static final Set<String> FAILED = new HashSet<>();

    /** 当前帧的地图叠加上下文，null 表示无地图打开。 */
    private static MapOverlayContext currentContext;

    static {
        // 按优先级注册：Xaero 优先于 JourneyMap
        PROVIDERS.add(new XaeroMapOverlayProvider());
        PROVIDERS.add(new JourneyMapOverlayProvider());
    }

    /**
     * 每 tick 调用，检测地图是否打开并捕获上下文。
     *
     * <p>由 {@link net.jsmua.kinetic_planner.KineticPlannerClient#onClientTickPost} 调用。
     */
    public static void tick() {
        Screen screen = Minecraft.getInstance().screen;
        if (screen == null) {
            currentContext = null;
            return;
        }
        for (MapOverlayProvider p : PROVIDERS) {
            if (FAILED.contains(p.modId())) continue;
            try {
                if (p.isMapOpen(screen)) {
                    currentContext = p.captureContext(screen);
                    return;
                }
            } catch (Throwable t) {
                KineticPlannerMod.LOGGER.error("MapOverlayProvider {} failed, circuit-breaking", p.modId(), t);
                FAILED.add(p.modId());
            }
        }
        currentContext = null;
    }

    /**
     * 返回当前帧的地图叠加上下文。
     *
     * @return {@link Optional} 包含上下文，若地图未打开则为空
     */
    public static Optional<MapOverlayContext> currentContext() {
        return Optional.ofNullable(currentContext);
    }
}
