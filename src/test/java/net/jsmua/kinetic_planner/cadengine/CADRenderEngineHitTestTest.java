package net.jsmua.kinetic_planner.cadengine;

import net.jsmua.kinetic_planner.instrument.GeometryCache;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link CADRenderEngine#hitTest} 命中检测单元测试。
 *
 * <p>spec §6.12：hitTest 在屏幕坐标空间对节点+边做命中检测。
 */
class CADRenderEngineHitTestTest {

    @Test
    void hitTestReturnsNullWhenNoGeometries() {
        CADRenderEngine engine = new CADRenderEngine();
        var result = engine.hitTest(10.0, 10.0, null, List.of());
        assertNull(result);
    }

    @Test
    void hitTestFindsNodeWithinThreshold() {
        CADRenderEngine engine = new CADRenderEngine();
        // 节点位于世界 (0, 0)，transform identity，屏幕 (0, 0) 附近命中
        var geom = new GeometryCache.GraphGeometry(
            UUID.randomUUID(), 0xFFFFFFFF,
            java.util.List.of(new Vec3(0, 0, 0)),
            java.util.List.of(),
            java.util.List.of()
        );
        var result = engine.hitTest(2.0, 2.0, null, List.of(geom));
        assertNotNull(result);
        assertTrue(result.type() == CADRenderEngine.HitResult.Type.NODE);
    }
}
