package net.jsmua.kinetic_planner.mapadapter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link MapOverlayContextProvider} 接口契约测试。
 *
 * <p>接口本身无逻辑，仅验证默认方法返回 null（便于 KpEditorScreen 未持有 GuiMap 时安全返回）。
 */
class MapOverlayContextProviderTest {

    @Test
    void defaultGetGuiMapReturnsNull() {
        MapOverlayContextProvider provider = () -> null;
        assertNull(provider.getGuiMap());
    }
}
