package net.jsmua.kinetic_planner.data;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.simibubi.create.content.trains.graph.TrackEdge;

@ExtendWith(MockitoExtension.class)
class RailwayDataAccessTest {
    @Mock TrackEdge mockEdge;

    @Test
    @Disabled("TrackEdge mock 受 JVM instrumentation 限制，Phase 0a 靠运行时验收")
    void edgeGeometryWithNullTurnReturnsStraight() {
        // Phase 0a: 靠 Task 10 运行时验收此分支
    }
}
