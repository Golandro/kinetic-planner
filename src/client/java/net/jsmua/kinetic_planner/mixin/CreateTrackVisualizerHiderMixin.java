package net.jsmua.kinetic_planner.mixin;

import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackGraphVisualizer;
import net.jsmua.kinetic_planner.config.KPConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 隐藏 Create 的信号边组叠加层（{@link TrackGraphVisualizer#visualiseSignalEdgeGroups}）。
 *
 * <p>当 KP 叠加层启用且未配置 {@code showCreateTrackMap} 时，cancel 该方法，
 * 完全跳过 Create 在 3D 世界中绘制信号边组彩色线条（玩家手持信号物品时触发）。
 *
 * <p>不干涉 {@code debugViewGraph}（F3 调试图视图）-- 那是 Create 自有调试功能，
 * 受 Create 自身配置 {@code showTrackGraphOnF3} 控制，与本模组无关。
 *
 * <p>调用链：{@code TrackTargetingClient.clientTick()} ->
 * {@code GlobalRailwayManager.tickSignalOverlay()} ->
 * {@code TrackGraphVisualizer.visualiseSignalEdgeGroups()}。
 *
 * <p>{@code remap = false} 因为目标类属于 Create mod（非 MC 原生类）。
 */
@Mixin(value = TrackGraphVisualizer.class, remap = false)
public class CreateTrackVisualizerHiderMixin {

    @Inject(method = "visualiseSignalEdgeGroups",
            at = @At("HEAD"), cancellable = true)
    private static void kp$hideSignalEdgeGroups(TrackGraph graph, CallbackInfo ci) {
        if (KPConfig.OVERLAY_ENABLED.get() && !KPConfig.SHOW_CREATE_TRACK_MAP.get()) {
            ci.cancel();
        }
    }
}
