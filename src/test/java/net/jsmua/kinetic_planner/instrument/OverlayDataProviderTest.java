package net.jsmua.kinetic_planner.instrument;

import net.jsmua.kinetic_planner.projection.WorldScreenTransform;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OverlayDataProvider 接口测试（审计 R5 测试缝修复）。
 */
class OverlayDataProviderTest {

    @Test
    void mockProviderReturnsNullWhenNoMap() {
        OverlayDataProvider provider = new OverlayDataProvider() {
            @Override
            public WorldScreenTransform getTransform() { return null; }
            @Override
            public GeometryCache getGeometryCache() { return null; }
        };
        assertNull(provider.getTransform());
        assertNull(provider.getGeometryCache());
    }

    @Test
    void mockProviderReturnsInjectedValues() {
        GeometryCache mockCache = new GeometryCache();
        OverlayDataProvider provider = new OverlayDataProvider() {
            @Override
            public WorldScreenTransform getTransform() { return null; }
            @Override
            public GeometryCache getGeometryCache() { return mockCache; }
        };
        assertSame(mockCache, provider.getGeometryCache());
    }
}
