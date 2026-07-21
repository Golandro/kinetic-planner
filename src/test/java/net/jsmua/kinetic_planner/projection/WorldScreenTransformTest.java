package net.jsmua.kinetic_planner.projection;

import org.joml.Vector2f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldScreenTransformTest {
    private static final double TOL = 1e-6;

    @Test
    void worldToScreenAtCenterReturnsScreenCenter() {
        CameraParams cam = new CameraParams(100.0, 200.0, 1.0, 800, 600);
        WorldScreenTransform t = new WorldScreenTransform(cam);
        Vector2f result = t.worldToScreen(100.0, 200.0);
        assertEquals(400.0, result.x, TOL);
        assertEquals(300.0, result.y, TOL);
    }

    @Test
    void worldToScreenAppliesBlocksPerPixelScale() {
        CameraParams cam = new CameraParams(0.0, 0.0, 2.0, 800, 600);
        WorldScreenTransform t = new WorldScreenTransform(cam);
        Vector2f result = t.worldToScreen(10.0, 20.0);
        assertEquals(405.0, result.x, TOL);
        assertEquals(310.0, result.y, TOL);
    }

    @Test
    void screenToWorldInvertsWorldToScreen() {
        CameraParams cam = new CameraParams(123.4, -56.7, 0.5, 1920, 1080);
        WorldScreenTransform t = new WorldScreenTransform(cam);
        double worldX = 1000.0;
        double worldZ = -2000.0;
        Vector2f screen = t.worldToScreen(worldX, worldZ);
        Vec2d back = t.screenToWorld(screen.x, screen.y);
        // float 精度损失：double->float->double 往返误差约 1e-3
        assertEquals(worldX, back.x(), 1e-3);
        assertEquals(worldZ, back.y(), 1e-3);
    }

    @Test
    void screenToWorldDistanceScalesByBlocksPerPixel() {
        CameraParams cam = new CameraParams(0.0, 0.0, 3.0, 800, 600);
        WorldScreenTransform t = new WorldScreenTransform(cam);
        assertEquals(30.0, t.screenToWorldDistance(10.0), TOL);
    }

    @Test
    void visibleWorldRectCoversScreenBounds() {
        CameraParams cam = new CameraParams(100.0, 100.0, 1.0, 800, 600);
        WorldScreenTransform t = new WorldScreenTransform(cam);
        WorldRect rect = t.visibleWorldRect();
        assertEquals(-300.0, rect.minX(), TOL);
        assertEquals(500.0, rect.maxX(), TOL);
        assertEquals(-200.0, rect.minZ(), TOL);
        assertEquals(400.0, rect.maxZ(), TOL);
    }

    @Test
    void visibleWorldRectClampsToWorldBoundary() {
        CameraParams cam = new CameraParams(0.0, 0.0, 1e8, 800, 600);
        WorldScreenTransform t = new WorldScreenTransform(cam);
        WorldRect rect = t.visibleWorldRect();
        assertTrue(rect.minX() >= -3.0e7);
        assertTrue(rect.maxX() <= 3.0e7);
        assertTrue(rect.minZ() >= -3.0e7);
        assertTrue(rect.maxZ() <= 3.0e7);
    }

    @Test
    void camAccessorReturnsConstructorValue() {
        CameraParams cam = new CameraParams(1.0, 2.0, 3.0, 4, 5);
        WorldScreenTransform t = new WorldScreenTransform(cam);
        assertSame(cam, t.cam());
    }
}
