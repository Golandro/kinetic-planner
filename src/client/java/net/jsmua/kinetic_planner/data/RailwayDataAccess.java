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

public class RailwayDataAccess implements IRailwayDataAccess {
    @Override
    public Stream<TrackGraph> graphsInDimension(ResourceKey<Level> dim) {
        return CreateClient.RAILWAYS.trackNetworks.values().stream();
    }

    @Override
    public Stream<TrackNode> nodesInDimension(TrackGraph g, ResourceKey<Level> dim) {
        return g.getNodes().stream()
            .filter(loc -> loc.dimension.equals(dim))
            .map(g::locateNode)
            .filter(Objects::nonNull);
    }

    @Override
    public Stream<TrackEdge> edgesFrom(TrackNode node) {
        // TODO: 从 node 所属 graph 的 connectionsByNode 获取出边
        return Stream.empty();
    }

    @Override
    public <T extends TrackEdgePoint> Stream<T> edgePoints(TrackGraph g, EdgePointType<T> type) {
        return g.getPoints(type).stream();
    }

    @Override
    public int clientVersion() {
        return CreateClient.RAILWAYS.version;
    }

    @Override
    public EdgeGeometry edgeGeometry(TrackEdge edge) {
        BezierConnection turn = edge.getTurn();
        if (turn == null) {
            Vec3 p1 = edge.node1 != null ? nodeWorldPos(edge.node1) : Vec3.ZERO;
            Vec3 p2 = edge.node2 != null ? nodeWorldPos(edge.node2) : Vec3.ZERO;
            return EdgeGeometry.straight(p1, p2);
        }
        // Create 三次贝塞尔：starts 是端点，axes 是控制点方向
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
        return node.getLocation().getLocation();
    }
}
