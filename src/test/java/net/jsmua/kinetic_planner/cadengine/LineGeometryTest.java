package net.jsmua.kinetic_planner.cadengine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LineGeometryTest {
    private static final float TOL = 1e-5f;

    @Test
    void expandHorizontalLineProducesCorrectTriangleStrip() {
        float[] v = LineGeometry.expandLineToTriangleStrip(0, 0, 10, 0, 2.0f);
        assertEquals(8, v.length);
        // 水平线：x 坐标正确
        assertEquals(0, v[0], TOL);
        assertEquals(0, v[2], TOL);
        assertEquals(10, v[4], TOL);
        assertEquals(10, v[6], TOL);
        // y 坐标偏移 ±1（width/2 = 1）
        assertEquals(1.0f, Math.abs(v[1]), TOL);
        assertEquals(1.0f, Math.abs(v[3]), TOL);
        // v1 和 v2 的 y 应符号相反
        assertEquals(-v[1], v[3], TOL);
    }

    @Test
    void expandVerticalLineProducesCorrectTriangleStrip() {
        float[] v = LineGeometry.expandLineToTriangleStrip(5, 5, 5, 10, 4.0f);
        assertEquals(8, v.length);
        // 垂直线：y 坐标正确
        assertEquals(5, v[1], TOL);
        assertEquals(5, v[3], TOL);
        assertEquals(10, v[5], TOL);
        assertEquals(10, v[7], TOL);
        // x 坐标偏移 ±2（width/2 = 2）
        assertEquals(2.0f, Math.abs(v[0] - 5), TOL);
        assertEquals(2.0f, Math.abs(v[2] - 5), TOL);
        assertEquals(2.0f, Math.abs(v[4] - 5), TOL);
        assertEquals(2.0f, Math.abs(v[6] - 5), TOL);
        // v1 和 v2 的 x 偏移应符号相反
        assertEquals(-(v[0] - 5), v[2] - 5, TOL);
    }

    @Test
    void expandZeroLengthLineReturnsDegenerateQuad() {
        float[] v = LineGeometry.expandLineToTriangleStrip(3, 3, 3, 3, 2.0f);
        assertEquals(8, v.length);
        // 起点顶点：x=3, y=3±1
        assertEquals(3, v[0], TOL);
        assertEquals(4, v[1], TOL);  // y + width/2
        assertEquals(3, v[2], TOL);
        assertEquals(2, v[3], TOL);  // y - width/2
    }
}
