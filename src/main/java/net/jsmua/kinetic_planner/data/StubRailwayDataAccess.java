package net.jsmua.kinetic_planner.data;

import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.signal.TrackEdgePoint;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.stream.Stream;

/**
 * {@link IRailwayDataAccess} 的测试桩实现，用于纯 JVM 单测。
 *
 * <p>不依赖 Minecraft 客户端环境，所有方法返回硬编码或空数据。
 * 通过 {@link #addGraph} 可手动添加图并递增版本号，验证 {@link GeometryCache} 的脏检测逻辑。
 */
public class StubRailwayDataAccess implements IRailwayDataAccess {

    /** 测试用的图列表。 */
    private final List<TrackGraph> graphs = new ArrayList<>();

    /** 模拟版本号，每次 {@link #addGraph} 递增。 */
    private int version = 0;

    /**
     * 添加一个轨道图到测试桩中，并递增版本号。
     *
     * @param g 轨道图（测试中可传 null）
     */
    public void addGraph(TrackGraph g) {
        graphs.add(g);
        version++;
    }

    @Override
    public Stream<TrackGraph> graphsInDimension(ResourceKey<Level> dim) {
        return graphs.stream();
    }

    @Override
    public Stream<TrackNode> nodesInDimension(TrackGraph g, ResourceKey<Level> dim) {
        return Stream.empty();
    }

    @Override
    public Stream<TrackEdge> edgesFrom(TrackNode node) {
        return Stream.empty();
    }

    @Override
    public <T extends TrackEdgePoint> Stream<T> edgePoints(TrackGraph g, EdgePointType<T> type) {
        return Stream.empty();
    }

    @Override
    public int clientVersion() {
        return version;
    }

    @Override
    public EdgeGeometry edgeGeometry(TrackEdge edge) {
        // 返回固定的直线几何（从原点到 (1,0,0)），忽略实际 edge 参数
        Vec3 p1 = Vec3.ZERO;
        Vec3 p2 = new Vec3(1, 0, 0);
        return EdgeGeometry.straight(p1, p2);
    }

    @Override
    public Vec3 nodeWorldPos(TrackNode node) {
        // TrackNode.getLocation() 返回 TrackNodeLocation (extends Vec3i)
        // TrackNodeLocation.getLocation() 返回 Vec3 (含 2x 压缩解压)
        return node.getLocation().getLocation();
    }
}
