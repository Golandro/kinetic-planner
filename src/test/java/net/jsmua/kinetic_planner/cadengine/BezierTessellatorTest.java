package net.jsmua.kinetic_planner.cadengine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BezierTessellatorTest {

    @Test
    void tessellateReturnsSegmentsPlusOnePoints() {
        float[] pts = BezierTessellator.tessellate(0, 0, 0, 10, 10, 10, 10, 0, 16);
        assertEquals(34, pts.length); // (16+1) * 2
    }

    @Test
    void tessellateEndpointsMatchControlPoints() {
        float[] pts = BezierTessellator.tessellate(0, 0, 0, 10, 10, 10, 10, 0, 8);
        // t=0 -> p0
        assertEquals(0, pts[0], 1e-5f);
        assertEquals(0, pts[1], 1e-5f);
        // t=1 -> p3
        assertEquals(10, pts[16], 1e-5f);
        assertEquals(0, pts[17], 1e-5f);
    }

    @Test
    void tessellateStraightLineIsLinear() {
        // 退化为直线：(0,0)->(0,0)->(10,0)->(10,0) 应得到直线
        float[] pts = BezierTessellator.tessellate(0, 0, 0, 0, 10, 0, 10, 0, 4);
        // 4 segments -> 5 points: t=0, 0.25, 0.5, 0.75, 1.0
        // pts[4] = x at t=0.5, pts[5] = y at t=0.5
        assertEquals(5, pts[4], 1e-4f);
        assertEquals(0, pts[5], 1e-4f);
    }
}
