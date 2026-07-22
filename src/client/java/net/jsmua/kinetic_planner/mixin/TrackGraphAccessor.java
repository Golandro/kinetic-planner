package net.jsmua.kinetic_planner.mixin;

import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/**
 * Mixin accessor for Create's {@link TrackGraph} package-private field {@code connectionsByNode}.
 *
 * <p>Provides read-only access to the internal {@code Map<TrackNode, Map<TrackNode, TrackEdge>>}
 * so that {@link net.jsmua.kinetic_planner.data.RailwayDataAccess} can enumerate edges.
 *
 * <p>{@code remap = false} because TrackGraph is a Create class, not a Mojang class.
 * Uses {@code kp$} prefix to avoid conflicts with other mods' mixins.
 */
@Mixin(value = TrackGraph.class, remap = false)
public interface TrackGraphAccessor {

    @Accessor("connectionsByNode")
    Map<TrackNode, Map<TrackNode, TrackEdge>> kp$getConnectionsByNode();
}
