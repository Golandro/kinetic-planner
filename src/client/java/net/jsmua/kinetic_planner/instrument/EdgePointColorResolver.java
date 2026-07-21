package net.jsmua.kinetic_planner.instrument;

import com.simibubi.create.CreateClient;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.signal.SignalBoundary;
import com.simibubi.create.content.trains.signal.SignalEdgeGroup;
import com.simibubi.create.content.trains.signal.TrackEdgePoint;
import com.simibubi.create.content.trains.station.GlobalStation;
import com.simibubi.create.content.trains.observer.TrackObserver;

/**
 * 边点颜色解析器，将 Create 的边点类型（信号、车站、观察器）映射为 ARGB 颜色值。
 *
 * <p>着色策略（委托 Create 内部颜色，无自定义主题）：
 * <ul>
 *   <li>{@link SignalBoundary} - 取 {@code SignalEdgeGroup.color}（EdgeGroupColor 枚举）
 *       的 RGB 值。信号边界有 {@code groups}（{@code Couple<UUID>}），取第一侧的颜色。</li>
 *   <li>{@link GlobalStation} - 无 mapColor 关联（已核实 Create 6.0.10 源码），
 *       fallback 到 {@code graph.color}</li>
 *   <li>{@link TrackObserver} - 无 graph.color 引用（已核实），fallback 到 {@code graph.color}</li>
 * </ul>
 *
 * <p>所有分支均用 try-catch 包裹，异常时 fallback 到 {@code graph.color}，
 * 确保颜色解析不会导致渲染崩溃。
 *
 * <h2>Create 6.0.10 API 适配说明</h2>
 * <ul>
 *   <li>{@code SignalBoundary.groupId} 不存在，实际字段是 {@code groups}（{@code Couple<UUID>}）</li>
 *   <li>{@code SignalEdgeGroup.color} 是 {@code EdgeGroupColor} 枚举（不是 Color），
 *       需调 {@code .get()} 获取 {@code Color} 后再 {@code .getRGB()}</li>
 *   <li>{@code TrackGraph.color} 是 {@code net.createmod.catnip.theme.Color} 类型，有 {@code getRGB()}</li>
 * </ul>
 */
public class EdgePointColorResolver {

    /**
     * 解析边点的显示颜色。
     *
     * @param point Create 边点（信号边界/车站/观察器）
     * @param graph 边点所属的轨道图（用于 fallback 颜色）
     * @return ARGB 颜色值
     */
    public static int resolve(TrackEdgePoint point, TrackGraph graph) {
        try {
            if (point instanceof SignalBoundary sb) {
                // SignalBoundary 有两个方向的信号组（Couple<UUID>），取第一侧
                SignalEdgeGroup group = CreateClient.RAILWAYS.signalEdgeGroups.get(sb.groups.getFirst());
                // EdgeGroupColor.get() 返回 Color，再 getRGB() 得到 int
                return group != null ? group.color.get().getRGB() : graph.color.getRGB();
            }
            // GlobalStation: 无 mapColor 关联（已核实 Create 6.0.10 源码），fallback graph.color
            // TrackObserver: 无 graph.color 引用（已核实），fallback graph.color
            return graph.color.getRGB();
        } catch (Throwable t) {
            // 任何异常（null graph、字段缺失等）都 fallback 到 graph.color
            return graph.color.getRGB();
        }
    }
}
