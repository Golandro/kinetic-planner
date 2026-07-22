package net.jsmua.kinetic_planner.data;

import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.signal.TrackEdgePoint;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.stream.Stream;

/**
 * 只读铁路数据访问接口，封装对 Create {@code TrackGraph} 的读取操作。
 *
 * <p>本接口是 common 侧（main sourceSet）的纯抽象，不引用任何客户端类。
 * 生产实现 {@link net.jsmua.kinetic_planner.data.RailwayDataAccess}（client sourceSet）
 * 通过 {@code CreateClient.RAILWAYS} 访问运行时数据；
 * 测试桩 {@link StubRailwayDataAccess} 用于纯 JVM 单测。
 *
 * <h2>只读约束</h2>
 * <p>实现类<strong>绝不</strong>调用 {@code addNode}/{@code connectNodes}/{@code putGraph}/
 * {@code removePoint} 等 mutate 方法。本接口仅提供查询语义。
 *
 * <h2>维度过滤</h2>
 * <p>Create 的 {@code TrackGraph} 可能跨维度。{@link #graphsInDimension} 返回所有图，
 * {@link #nodesInDimension} 在图内按维度过滤节点。
 */
public interface IRailwayDataAccess {

    /**
     * 返回所有轨道图（不按维度过滤）。
     *
     * <p>维度过滤在 {@link #nodesInDimension} 内进行，因为同一 {@code TrackGraph}
     * 可能包含多个维度的节点（跨维度轨道）。
     *
     * @param dim 当前维度（保留参数，Phase 0a 不用于过滤图）
     * @return 所有轨道图的流
     */
    Stream<TrackGraph> graphsInDimension(ResourceKey<Level> dim);

    /**
     * 返回指定图中属于给定维度的轨道节点。
     *
     * <p>实现需通过 {@code TrackGraph.getNodes()}（返回 {@code Set<TrackNodeLocation>}）
     * 按维度过滤后，用 {@code TrackGraph.locateNode()} 转换为 {@code TrackNode}。
     *
     * @param g   轨道图
     * @param dim 目标维度
     * @return 该维度内的节点流
     */
    Stream<TrackNode> nodesInDimension(TrackGraph g, ResourceKey<Level> dim);

    /**
     * 返回给定图中给定节点的所有出边。
     *
     * <p>通过 {@code TrackGraph.connectionsByNode}（package-private）获取，
     * 需 Mixin accessor（{@link net.jsmua.kinetic_planner.mixin.TrackGraphAccessor}）。
     *
     * @param graph 轨道图（提供 connectionsByNode）
     * @param node  起始节点
     * @return 出边流
     */
    Stream<TrackEdge> edgesFrom(TrackGraph graph, TrackNode node);

    /**
     * 返回指定图中给定类型的边点（信号、车站、观察器等）。
     *
     * @param g    轨道图
     * @param type 边点类型（{@code EdgePointType.TYPES} 注册表中的类型）
     * @param <T>  边点子类型
     * @return 边点流
     */
    <T extends TrackEdgePoint> Stream<T> edgePoints(TrackGraph g, EdgePointType<T> type);

    /**
     * 返回客户端铁路数据的版本号，用于脏检测。
     *
     * <p>当版本号变化时，{@link net.jsmua.kinetic_planner.instrument.GeometryCache}
     * 会触发几何数据重建。
     *
     * @return 当前版本号（对应 {@code CreateClient.RAILWAYS.version}）
     */
    int clientVersion();

    /**
     * 从 Create 的 {@code TrackEdge} 提取几何描述符。
     *
     * <p>对于直线轨道，返回端点坐标。对于贝塞尔曲线轨道，提取 4 个控制点
     * （来自 {@code BezierConnection.starts} 和 {@code BezierConnection.axes}）。
     *
     * @param edge Create 轨道边
     * @return 几何描述符
     */
    EdgeGeometry edgeGeometry(TrackEdge edge);

    /**
     * 返回轨道节点在世界坐标系中的位置。
     *
     * <p>实现需调用 {@code node.getLocation().getLocation()}：
     * 第一个 {@code getLocation()} 返回 {@code TrackNodeLocation}（继承 {@code Vec3i}），
     * 第二个 {@code getLocation()} 返回 {@code Vec3}（含 2 倍压缩解压 + Y 偏移修正）。
     *
     * @param node 轨道节点
     * @return 世界坐标
     */
    Vec3 nodeWorldPos(TrackNode node);
}
