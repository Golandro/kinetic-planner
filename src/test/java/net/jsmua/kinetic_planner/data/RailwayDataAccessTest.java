package net.jsmua.kinetic_planner.data;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class RailwayDataAccessTest {
    @Mock TrackEdge mockEdge;
    @Mock TrackGraph mockGraph;
    @Mock TrackNode mockNode;

    @Test
    @Disabled("TrackEdge mock 受 JVM instrumentation 限制，Phase 0a 靠运行时验收")
    void edgeGeometryWithNullTurnReturnsStraight() {
        // Phase 0a: 靠 Task 10 运行时验收此分支
    }

    @Test
    @Disabled("TrackGraph mock 受 JVM instrumentation 限制，靠运行时验收")
    void edgesFromReturnsEmptyWhenGraphNotAccessor() {
        // mockGraph 不是 TrackGraphAccessor，会抛 ClassCastException，catch 后返回空
        RailwayDataAccess access = new RailwayDataAccess();
        var edges = access.edgesFrom(mockGraph, mockNode);
        assertEquals(0, edges.count());
    }
}
