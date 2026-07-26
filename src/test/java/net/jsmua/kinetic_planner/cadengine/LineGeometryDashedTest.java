package net.jsmua.kinetic_planner.cadengine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LineGeometryDashedTest {

    @Test
    void horizontalLineProducesCorrectDashCount() {
        // 10 像素线段，dash=2, gap=1 -> 3 dashes (0-2, 3-5, 6-8), 最后 9 不够一个 dash
        float[][] segments = LineGeometry.buildDashedSegments(0, 0, 10, 0, 2, 1);
        assertEquals(3, segments.length);
        // 第一段: 0->2
        assertEquals(0f, segments[0][0], 1e-6f);
        assertEquals(0f, segments[0][1], 1e-6f);
        assertEquals(2f, segments[0][2], 1e-6f);
        assertEquals(0f, segments[0][3], 1e-6f);
    }

    @Test
    void zeroLengthReturnsEmptyArray() {
        float[][] segments = LineGeometry.buildDashedSegments(5, 5, 5, 5, 2, 1);
        assertEquals(0, segments.length);
    }

    @Test
    void verticalLineCorrectSegments() {
        float[][] segments = LineGeometry.buildDashedSegments(0, 0, 0, 6, 2, 1);
        assertEquals(2, segments.length);
        // 第一段: (0,0) -> (0,2)
        assertEquals(0f, segments[0][0], 1e-6f);
        assertEquals(0f, segments[0][1], 1e-6f);
        assertEquals(0f, segments[0][2], 1e-6f);
        assertEquals(2f, segments[0][3], 1e-6f);
        // 第二段: (0,3) -> (0,5)
        assertEquals(0f, segments[1][0], 1e-6f);
        assertEquals(3f, segments[1][1], 1e-6f);
        assertEquals(0f, segments[1][2], 1e-6f);
        assertEquals(5f, segments[1][3], 1e-6f);
    }
}
