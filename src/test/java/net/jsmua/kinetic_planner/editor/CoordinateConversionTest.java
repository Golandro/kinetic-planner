package net.jsmua.kinetic_planner.editor;

import net.jsmua.kinetic_planner.projection.CameraParams;
import net.jsmua.kinetic_planner.projection.WorldScreenTransform;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 坐标转换往返测试（spec §10.1）。
 *
 * <p>验证屏幕坐标 -> 世界坐标 -> 屏幕坐标的往返一致性。
 * 使用 common sourceSet 的 {@link WorldScreenTransform}，纯 JVM 可测。
 */
class CoordinateConversionTest {

    @Test
    void roundTripAtDefaultScale() {
        var cam = new CameraParams(100.0, 200.0, 1.0, 960, 540);
        var transform = new WorldScreenTransform(cam);

        // 屏幕中心 -> 世界 -> 屏幕
        double screenX = 480, screenY = 270;
        var world = transform.screenToWorld(screenX, screenY);
        var back = transform.worldToScreen(world.x(), world.y());

        assertEquals(screenX, back.x, 0.001, "X 往返不一致");
        assertEquals(screenY, back.y, 0.001, "Y 往返不一致");
    }

    @Test
    void roundTripAtHighZoom() {
        var cam = new CameraParams(500.0, -300.0, 0.1, 960, 540);
        var transform = new WorldScreenTransform(cam);

        double screenX = 100, screenY = 200;
        var world = transform.screenToWorld(screenX, screenY);
        var back = transform.worldToScreen(world.x(), world.y());

        assertEquals(screenX, back.x, 0.001);
        assertEquals(screenY, back.y, 0.001);
    }

    @Test
    void roundTripAtLowZoom() {
        var cam = new CameraParams(0.0, 0.0, 16.0, 960, 540);
        var transform = new WorldScreenTransform(cam);

        double screenX = 480, screenY = 270;
        var world = transform.screenToWorld(screenX, screenY);
        var back = transform.worldToScreen(world.x(), world.y());

        assertEquals(screenX, back.x, 0.001);
        assertEquals(screenY, back.y, 0.001);
    }

    @Test
    void worldOriginMapsToScreenCenter() {
        var cam = new CameraParams(100.0, 200.0, 1.0, 960, 540);
        var transform = new WorldScreenTransform(cam);

        var screen = transform.worldToScreen(100.0, 200.0);
        assertEquals(480, screen.x, 0.001, "世界原点应映射到屏幕中心 X");
        assertEquals(270, screen.y, 0.001, "世界原点应映射到屏幕中心 Y");
    }
}
