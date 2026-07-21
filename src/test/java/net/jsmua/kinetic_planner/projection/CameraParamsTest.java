package net.jsmua.kinetic_planner.projection;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CameraParamsTest {
    @Test
    void screenCenterReturnsHalfDimensions() {
        CameraParams cam = new CameraParams(0.0, 0.0, 1.0, 800, 600);
        assertEquals(400, cam.screenCenterX());
        assertEquals(300, cam.screenCenterY());
    }

    @Test
    void oddScreenSizeFloorsCenter() {
        CameraParams cam = new CameraParams(0.0, 0.0, 1.0, 801, 601);
        assertEquals(400, cam.screenCenterX());
        assertEquals(300, cam.screenCenterY());
    }
}

class WorldRectTest {
    @Test
    void containsInsideBoundsReturnsTrue() {
        WorldRect r = new WorldRect(-10.0, -10.0, 10.0, 10.0);
        assertTrue(r.contains(0.0, 0.0));
        assertTrue(r.contains(-10.0, -10.0));
        assertTrue(r.contains(10.0, 10.0));
    }

    @Test
    void containsOutsideBoundsReturnsFalse() {
        WorldRect r = new WorldRect(-10.0, -10.0, 10.0, 10.0);
        assertFalse(r.contains(-10.1, 0.0));
        assertFalse(r.contains(0.0, 10.1));
    }

    @Test
    void containsWithMarginExpandsBounds() {
        WorldRect r = new WorldRect(0.0, 0.0, 0.0, 0.0);
        assertFalse(r.contains(1.0, 0.0));
        assertTrue(r.containsWithMargin(1.0, 0.0, 1.0));
    }
}
