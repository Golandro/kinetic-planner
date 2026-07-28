package net.jsmua.kinetic_planner.mapadapter;

import net.jsmua.kinetic_planner.KineticPlannerMod;
import net.jsmua.kinetic_planner.config.IKPConfig;
import net.jsmua.kinetic_planner.config.KPConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.*;
import javax.annotation.Nullable;

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
 *   <li>捕获成功则暂存上下文与 active provider modId 并停止轮询；捕获失败则熔断该 provider</li>
 * </ol>
 *
 * <h2>熔断机制</h2>
 * <p>当某个 provider 在 {@code isMapOpen} 或 {@code captureContext} 中抛出异常时，
 * 其 modId 和当前游戏 tick 被记录到 {@link #FAILED} 映射，后续 tick 在冷却期内跳过该 provider。
 * 冷却期（{@value #CIRCUIT_BREAK_COOLDOWN} tick = 10 秒）到期后自动重试一次。
 * 重试仍失败则重新记录熔断时间，延长冷却。
 *
 * <p>可通过 {@link #resetCircuitBreaker(String)} 手动清除熔断状态，
 * 或通过命令 {@code /kp provider reset-circuit [modId]} 触发。
 */
public class MapOverlayDispatcher {

    /** 已注册的地图 provider 列表，按优先级排序。 */
    private static final List<MapOverlayProvider> PROVIDERS = new ArrayList<>();

    /** 已熔断的 provider modId -> 熔断时的游戏时间（tick）。 */
    private static final Map<String, Long> FAILED = new HashMap<>();

    /** 熔断冷却时间（tick），到期后自动重试一次。200 tick = 10 秒。 */
    private static final long CIRCUIT_BREAK_COOLDOWN = 200;

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
        long currentTick = Minecraft.getInstance().level != null
            ? Minecraft.getInstance().level.getGameTime()
            : 0;
        // 按 priority 排序（数字越小越优先），provider 仅 2 个，排序开销可忽略
        List<MapOverlayProvider> sorted = PROVIDERS.stream()
            .sorted(Comparator.comparingInt(p -> {
                var config = KPConfig.getInstance().getProviderConfig(p.modId());
                return config != null ? config.priority() : Integer.MAX_VALUE;
            }))
            .toList();
        for (MapOverlayProvider p : sorted) {
            String modId = p.modId();
            // 检查熔断状态
            Long failedAt = FAILED.get(modId);
            if (failedAt != null) {
                long elapsed = currentTick - failedAt;
                if (elapsed < CIRCUIT_BREAK_COOLDOWN) continue;
                // 冷却到期，自动重试
                FAILED.remove(modId);
                KineticPlannerMod.LOGGER.info("Circuit breaker for {} expired, retrying", modId);
            }
            // 读 per-provider enabled 配置
            var config = KPConfig.getInstance().getProviderConfig(p.modId());
            if (config != null && !config.enabled()) continue;
            try {
                if (p.isMapOpen(screen)) {
                    currentContext = p.captureContext(screen);
                    activeProviderModId = p.modId();
                    return;
                }
            } catch (Throwable t) {
                KineticPlannerMod.LOGGER.error("MapOverlayProvider {} failed, circuit-breaking", modId, t);
                FAILED.put(modId, currentTick);
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

    /**
     * 重置熔断状态。
     *
     * @param modId provider mod ID；null 表示重置全部
     */
    public static void resetCircuitBreaker(@Nullable String modId) {
        if (modId != null) {
            FAILED.remove(modId);
        } else {
            FAILED.clear();
        }
    }

    /**
     * 查询指定 provider 是否处于熔断状态。
     *
     * @param modId provider mod ID
     * @return true 如果该 provider 当前被熔断
     */
    public static boolean isCircuitBroken(String modId) {
        return FAILED.containsKey(modId);
    }

    /**
     * 返回所有当前被熔断的 provider modId 集合（快照）。
     *
     * @return 不可变 modId 集合
     */
    public static Set<String> getFailedProviders() {
        return Set.copyOf(FAILED.keySet());
    }
}
