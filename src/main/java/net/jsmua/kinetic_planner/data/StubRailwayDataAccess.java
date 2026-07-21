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

public class StubRailwayDataAccess implements IRailwayDataAccess {
    private final List<TrackGraph> graphs = new ArrayList<>();
    private int version = 0;

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
        Vec3 p1 = Vec3.ZERO;
        Vec3 p2 = new Vec3(1, 0, 0);
        return EdgeGeometry.straight(p1, p2);
    }

    @Override
    public Vec3 nodeWorldPos(TrackNode node) {
        return node.getLocation().getLocation();
    }
}
