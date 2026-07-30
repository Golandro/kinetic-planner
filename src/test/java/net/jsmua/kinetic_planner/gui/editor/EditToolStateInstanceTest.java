package net.jsmua.kinetic_planner.gui.editor;

import net.jsmua.kinetic_planner.gui.editor.EditToolState.Tool;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link EditToolState} 包级构造器测试（审计 R5 测试缝修复）。
 *
 * <p>必须在 {@code gui.editor} 包中才能访问包级构造器 {@link EditToolState#EditToolState(boolean)}。
 */
class EditToolStateInstanceTest {

    @Test
    void packagePrivateConstructorCreatesIndependentInstance() {
        EditToolState testState = new EditToolState(true);
        // 验证独立实例不受单例影响
        testState.setCurrentTool(Tool.SELECT);
        assertEquals(Tool.SELECT, testState.getCurrentTool());
        // 单例状态不受影响
        assertNotEquals(Tool.SELECT, EditToolState.getInstance().getCurrentTool());
    }

    @Test
    void packagePrivateInstanceHasIndependentSelection() {
        EditToolState testState = new EditToolState(true);
        testState.addSelectedNode(java.util.UUID.randomUUID());
        // 单例的选择集不受影响
        assertTrue(EditToolState.getInstance().getSelectedNodes().isEmpty());
        assertEquals(1, testState.getSelectedNodes().size());
    }
}
