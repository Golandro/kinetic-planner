package net.jsmua.kinetic_planner.editor;

import net.jsmua.kinetic_planner.cadengine.EditLayerRenderer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 EditLayerRenderer 不导入 KpEditorScreen（消除编译期循环依赖）。
 *
 * <p>审计发现 R5：EditLayerRenderer -> KpEditorScreen -> EditLayerRenderer 循环。
 * 修复后 EditLayerRenderer 不应再引用 KpEditorScreen。
 */
class CadLayerNoCycleTest {

    @Test
    void editLayerRendererDoesNotImportKpEditorScreen() throws ClassNotFoundException {
        Class<?> editLayerRenderer = EditLayerRenderer.class;
        // 获取所有 declared fields 的类型，确认不含 KpEditorScreen
        for (var field : editLayerRenderer.getDeclaredFields()) {
            assertNotEquals("net.jsmua.kinetic_planner.gui.editor.KpEditorScreen",
                field.getType().getName(),
                "EditLayerRenderer 不应持有 KpEditorScreen 字段（循环依赖）");
        }
        // 获取所有 declared methods 的参数和返回类型
        for (var method : editLayerRenderer.getDeclaredMethods()) {
            assertNotEquals("net.jsmua.kinetic_planner.gui.editor.KpEditorScreen",
                method.getReturnType().getName(),
                "EditLayerRenderer 方法不应返回 KpEditorScreen（循环依赖）");
            for (var paramType : method.getParameterTypes()) {
                assertNotEquals("net.jsmua.kinetic_planner.gui.editor.KpEditorScreen",
                    paramType.getName(),
                    "EditLayerRenderer 方法参数不应为 KpEditorScreen（循环依赖）");
            }
        }
    }
}
