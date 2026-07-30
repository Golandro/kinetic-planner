package net.jsmua.kinetic_planner.editor;

import net.jsmua.kinetic_planner.gui.editor.KpEditorScreen;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 编辑器事件路由测试（spec §10.1）。
 *
 * <p>大多数行为测试需要 MC 运行时（Screen 构造触发 <clinit>），
 * 标记 @Disabled 靠 runClient 验收。结构验证（方法签名、常量移除）可在纯 JVM 执行。
 */
class EditorEventRoutingTest {

    /**
     * 验证自定义缩放常量已被移除（spec §4.3.2 要求委托 guiMap.mouseScrolled）。
     */
    @Test
    void noCustomScaleConstants() {
        assertThrows(NoSuchFieldException.class,
            () -> KpEditorScreen.class.getDeclaredField("MIN_SCALE"),
            "MIN_SCALE 应已移除（spec §4.3.2 委托 guiMap.mouseScrolled）");
        assertThrows(NoSuchFieldException.class,
            () -> KpEditorScreen.class.getDeclaredField("MAX_SCALE"),
            "MAX_SCALE 应已移除");
        assertThrows(NoSuchFieldException.class,
            () -> KpEditorScreen.class.getDeclaredField("SCALE_STEP"),
            "SCALE_STEP 应已移除");
    }

    /**
     * 验证 mouseScrolled 方法签名不变（委托后仍接收 4 参数）。
     */
    @Test
    void mouseScrolledSignature() throws NoSuchMethodException {
        Method m = KpEditorScreen.class.getDeclaredMethod(
            "mouseScrolled", double.class, double.class, double.class, double.class);
        assertEquals(boolean.class, m.getReturnType());
    }

    /**
     * 验证 mouseDragged 方法签名不变。
     */
    @Test
    void mouseDraggedSignature() throws NoSuchMethodException {
        Method m = KpEditorScreen.class.getDeclaredMethod(
            "mouseDragged", double.class, double.class, int.class, double.class, double.class);
        assertEquals(boolean.class, m.getReturnType());
    }

    @Test
    @Disabled("需要 MC 运行时：验证滚轮缩放委托 guiMap.mouseScrolled（所有工具）")
    void scrollDelegatesToGuiMapForAllTools() {
        // 运行时验收：
        // 1. 切换到 SELECT 工具
        // 2. 在主视口内滚轮
        // 3. 验证 guiMap.mouseScrolled 被调用（通过 spy/mock 或行为观察）
        // 4. 验证地图实际缩放（中心点保持）
    }

    @Test
    @Disabled("需要 MC 运行时：验证拖拽中右键取消")
    void rightClickCancelsDrag() {
        // 运行时验收：
        // 1. NAVIGATION 工具下左键开始拖拽
        // 2. 拖拽中按右键
        // 3. 验证 isDraggingMap = false，拖拽结束
    }

    @Test
    @Disabled("需要 MC 运行时：验证缩放中心点保持")
    void scrollPreservesCenterPoint() {
        // 运行时验收：
        // 1. 记录鼠标指针下方的世界坐标
        // 2. 滚轮缩放
        // 3. 验证该世界坐标仍在指针下方
    }

    /**
     * 验证 isMouseOverMapViewport 方法存在且为 private（spec §3.1 视口边界查询）。
     */
    @Test
    void isMouseOverMapViewportMethodExists() throws NoSuchMethodException {
        Method m = KpEditorScreen.class.getDeclaredMethod(
            "isMouseOverMapViewport", double.class, double.class);
        assertEquals(boolean.class, m.getReturnType());
        assertTrue(Modifier.isPrivate(m.getModifiers()),
            "isMouseOverMapViewport 应为 private");
    }

    /**
     * 验证 overlayProvider 字段存在（审计 R5 测试缝）。
     */
    @Test
    void overlayProviderFieldExists() throws NoSuchFieldException {
        var field = KpEditorScreen.class.getDeclaredField("overlayProvider");
        assertEquals("net.jsmua.kinetic_planner.instrument.OverlayDataProvider",
            field.getType().getName());
        assertTrue(Modifier.isPrivate(field.getModifiers()));
    }
}
