package net.jsmua.kinetic_planner.data;

import com.simibubi.create.CreateClient;
import com.simibubi.create.content.trains.graph.*;
import com.simibubi.create.content.trains.signal.TrackEdgePoint;
import com.simibubi.create.content.trains.track.BezierConnection;
import com.simibubi.create.content.trains.track.TrackMaterial;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.stream.Stream;

/**
 * {@link IRailwayDataAccess} 的生产实现（client sourceSet）。
 *
 * <p>通过 {@link CreateClient#RAILWAYS}（即 {@code GlobalRailwayManager}）访问
 * Create 运行时的轨道图数据。所有方法均为只读操作，不修改 Create 的内部状态。
 *
 * <h2>Create 6.0.10 API 适配说明</h2>
 * <ul>
 *   <li>{@code TrackGraph.getNodes()} 返回 {@code Set<TrackNodeLocation>}（不是 TrackNode），
 *       需通过 {@code locateNode()} 转换</li>
 *   <li>{@code TrackNode.getLocation()} 返回 {@code TrackNodeLocation}（继承 Vec3i），
 *       需再调 {@code .getLocation()} 得到 {@code Vec3}</li>
 *   <li>{@code TrackEdge.trackMaterial} 是 package-private，用 {@code getTrackMaterial()} 替代</li>
 *   <li>{@code BezierConnection.starts/axes} 是 {@code Couple<Vec3>}（不是 Pair），
 *       有 {@code getFirst()}/{@code getSecond()} 方法</li>
 *   <li>{@code BezierConnection} 是三次贝塞尔：starts 为端点，axes 为控制点方向向量（需加端点坐标得控制点）</li>
 * </ul>
 *
 * <h2>线程安全</h2>
 * <p>本类在客户端 tick 线程中使用，不涉及多线程。{@code CreateClient.RAILWAYS}
 * 的数据由 Create 在 tick 中更新，本类仅在 tick 后读取，无并发问题。
 */
public class RailwayDataAccess implements IRailwayDataAccess {

    @Override
    public Stream<TrackGraph> graphsInDimension(ResourceKey<Level> dim) {
        // 返回所有轨道图，维度过滤在 nodesInDimension 中做
        // （同一图可能跨维度，按图过滤会漏掉跨维度轨道）
        return CreateClient.RAILWAYS.trackNetworks.values().stream();
    }

    @Override
    public Stream<TrackNode> nodesInDimension(TrackGraph g, ResourceKey<Level> dim) {
        // TrackGraph.getNodes() 返回 Set<TrackNodeLocation>（keySet）
        // 需先按维度过滤 TrackNodeLocation，再用 locateNode() 转换为 TrackNode
        return g.getNodes().stream()
            .filter(loc -> loc.dimension.equals(dim))
            .map(g::locateNode)
            .filter(Objects::nonNull);
    }

    @Override
    public Stream<TrackEdge> edgesFrom(TrackGraph graph, TrackNode node) {
        // TODO Phase 0b Task 2 will implement this via TrackGraphAccessor
        return Stream.empty();
    }

    @Override
    public <T extends TrackEdgePoint> Stream<T> edgePoints(TrackGraph g, EdgePointType<T> type) {
        return g.getPoints(type).stream();
    }

    @Override
    public int clientVersion() {
        // GlobalRailwayManager.version 在每次轨道图变更时递增
        return CreateClient.RAILWAYS.version;
    }

    @Override
    public EdgeGeometry edgeGeometry(TrackEdge edge) {
        BezierConnection turn = edge.getTurn();
        if (turn == null) {
            // 直线轨道：端点取自 edge.node1/node2（public 字段）
            // null 检查防御 mock 环境下的 null 字段
            Vec3 p1 = edge.node1 != null ? nodeWorldPos(edge.node1) : Vec3.ZERO;
            Vec3 p2 = edge.node2 != null ? nodeWorldPos(edge.node2) : Vec3.ZERO;
            return EdgeGeometry.straight(p1, p2);
        }
        // Create 三次贝塞尔曲线：
        //   starts.getFirst()/getSecond() 是曲线端点（Vec3）
        //   axes.getFirst()/getSecond() 是控制点方向向量，需加上端点坐标得到控制点位置
        Vec3 start = turn.starts.getFirst();
        Vec3 end = turn.starts.getSecond();
        Vec3 control1 = turn.axes.getFirst().add(start);
        Vec3 control2 = turn.axes.getSecond().add(end);
        TrackMaterial material = edge.getTrackMaterial();
        EdgeGeometry.BezierSpec spec = new EdgeGeometry.BezierSpec(start, control1, control2, end, material);
        return EdgeGeometry.bezier(start, end, spec);
    }

    @Override
    public Vec3 nodeWorldPos(TrackNode node) {
        // TrackNode.getLocation() -> TrackNodeLocation (extends Vec3i, 2x 压缩坐标)
        // TrackNodeLocation.getLocation() -> Vec3 (解压为真实世界坐标)
        return node.getLocation().getLocation();
    }
}
