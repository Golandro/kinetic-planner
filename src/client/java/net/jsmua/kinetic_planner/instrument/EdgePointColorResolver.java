package net.jsmua.kinetic_planner.instrument;

import com.simibubi.create.CreateClient;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.signal.SignalBoundary;
import com.simibubi.create.content.trains.signal.SignalEdgeGroup;
import com.simibubi.create.content.trains.signal.TrackEdgePoint;
import com.simibubi.create.content.trains.station.GlobalStation;
import com.simibubi.create.content.trains.observer.TrackObserver;

public class EdgePointColorResolver {
    public static int resolve(TrackEdgePoint point, TrackGraph graph) {
        try {
            if (point instanceof SignalBoundary sb) {
                SignalEdgeGroup group = CreateClient.RAILWAYS.signalEdgeGroups.get(sb.groups.getFirst());
                return group != null ? group.color.get().getRGB() : graph.color.getRGB();
            }
            // GlobalStation: 无 mapColor 关联（已核实），fallback graph.color
            // TrackObserver: 无 graph.color 引用（已核实），fallback graph.color
            return graph.color.getRGB();
        } catch (Throwable t) {
            return graph.color.getRGB();
        }
    }
}
