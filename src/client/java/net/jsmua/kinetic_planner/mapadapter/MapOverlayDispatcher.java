package net.jsmua.kinetic_planner.mapadapter;

import net.jsmua.kinetic_planner.KineticPlannerMod;
import net.jsmua.kinetic_planner.config.KPConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.*;

/**
 * 地图叠加调度器，每 tick 轮询已注册的 {@link MapOverlayProvider} 并管理熔断。
 *
 * <p>调度流程（每客户端 tick 执行一次）：
 * <ol>
 *   <li>获取当前 {@code Minecraft.getInstance().screen}</li>
 *   <li>若屏幕为 null，清除上下文与 active provider 并返回</li>
 *   <li>按 {@link KPConfig#getProviderConfig} 的 priority 排序后遍历（数字越小越优先），
 *       跳过 disabled provider 与已熔断的</li>
 *   <li>若 {@link MapOverlayProvider#isMapOpen} 返回 true，调用 {@link MapOverlayProvider#captureContext}</li>
 *   <li>捕获成功则暂存上下文与 active provider modId 并停止轮询；捕获失败则熔断该 provider（不再重试）</li>
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

    /** 当前帧激活的 provider modId，null 表示无地图打开。 */
    private static String activeProviderModId;

    static {
        // 按优先级注册：Xaero 优先于 JourneyMap
        PROVIDERS.add(new XaeroMapOverlayProvider());
        PROVIDERS.add(new JourneyMapOverlayProvider());
    }

    /**
     * 返回所有已注册 provider 的不可变快照。
     *
     * @return provider 列表（快照）
     */
    public static List<MapOverlayProvider> registeredProviders() {
        return List.copyOf(PROVIDERS);
    }

    /**
     * 每 tick 调用，检测地图是否打开并捕获上下文。
     *
     * <p>按 priority 排序后遍历（数字越小越优先），跳过 disabled provider
     * （读 {@link KPConfig#getProviderConfig}）。
     *
     * <p>由 {@link net.jsmua.kinetic_planner.KineticPlannerClient#onClientTickPost} 调用。
     */
    public static void tick() {
        Screen screen = Minecraft.getInstance().screen;
        if (screen == null) {
            currentContext = null;
            activeProviderModId = null;
            return;
        }
        // 按 priority 排序（数字越小越优先），provider 仅 2 个，排序开销可忽略
        List<MapOverlayProvider> sorted = PROVIDERS.stream()
            .sorted(Comparator.comparingInt(p -> {
                var config = KPConfig.getProviderConfig(p.modId());
                return config != null ? config.priority() : Integer.MAX_VALUE;
            }))
            .toList();
        for (MapOverlayProvider p : sorted) {
            if (FAILED.contains(p.modId())) continue;
            // 读 per-provider enabled 配置
            var config = KPConfig.getProviderConfig(p.modId());
            if (config != null && !config.enabled()) continue;
            try {
                if (p.isMapOpen(screen)) {
                    currentContext = p.captureContext(screen);
                    activeProviderModId = p.modId();
                    return;
                }
            } catch (Throwable t) {
                KineticPlannerMod.LOGGER.error("MapOverlayProvider {} failed, circuit-breaking", p.modId(), t);
                FAILED.add(p.modId());
            }
        }
        currentContext = null;
        activeProviderModId = null;
    }

    /**
     * 返回当前帧的地图叠加上下文。
     *
     * @return {@link Optional} 包含上下文，若地图未打开则为空
     */
    public static Optional<MapOverlayContext> currentContext() {
        return Optional.ofNullable(currentContext);
    }

    /**
     * 返回当前激活的 provider modId。
     *
     * @return {@link Optional} 包含 modId，无地图打开时为空
     */
    public static Optional<String> activeProviderModId() {
        return Optional.ofNullable(activeProviderModId);
    }
}
