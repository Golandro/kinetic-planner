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

public interface IRailwayDataAccess {
    Stream<TrackGraph> graphsInDimension(ResourceKey<Level> dim);
    Stream<TrackNode> nodesInDimension(TrackGraph g, ResourceKey<Level> dim);
    Stream<TrackEdge> edgesFrom(TrackNode node);
    <T extends TrackEdgePoint> Stream<T> edgePoints(TrackGraph g, EdgePointType<T> type);
    int clientVersion();
    EdgeGeometry edgeGeometry(TrackEdge edge);
    Vec3 nodeWorldPos(TrackNode node);
}
