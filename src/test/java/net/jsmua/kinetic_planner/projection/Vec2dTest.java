package net.jsmua.kinetic_planner.projection;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Vec2dTest {
    @Test
    void accessorsReturnConstructorValues() {
        Vec2d v = new Vec2d(3.5, -2.0);
        assertEquals(3.5, v.x(), 1e-9);
        assertEquals(-2.0, v.y(), 1e-9);
    }

    @Test
    void addReturnsComponentWiseSum() {
        Vec2d a = new Vec2d(1.0, 2.0);
        Vec2d b = new Vec2d(3.0, 4.0);
        Vec2d result = a.add(b);
        assertEquals(4.0, result.x(), 1e-9);
        assertEquals(6.0, result.y(), 1e-9);
    }

    @Test
    void subtractReturnsComponentWiseDifference() {
        Vec2d a = new Vec2d(5.0, 7.0);
        Vec2d b = new Vec2d(2.0, 3.0);
        Vec2d result = a.subtract(b);
        assertEquals(3.0, result.x(), 1e-9);
        assertEquals(4.0, result.y(), 1e-9);
    }

    @Test
    void distanceToReturnsEuclideanDistance() {
        Vec2d a = new Vec2d(0.0, 0.0);
        Vec2d b = new Vec2d(3.0, 4.0);
        assertEquals(5.0, a.distanceTo(b), 1e-9);
    }

    @Test
    void scaleReturnsScaledVector() {
        Vec2d v = new Vec2d(2.0, -3.0);
        Vec2d result = v.scale(2.5);
        assertEquals(5.0, result.x(), 1e-9);
        assertEquals(-7.5, result.y(), 1e-9);
    }
}
