package net.jsmua.kinetic_planner.editor;

import net.jsmua.kinetic_planner.gui.editor.EditToolState;
import net.jsmua.kinetic_planner.gui.editor.EditToolState.Tool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link EditToolState} 单元测试（spec §4.3 + §6.8）。
 *
 * <p>EditToolState 是编辑会话状态（仅编辑模式活跃），与全局 KpClientState.editMode 生命周期不同。
 */
class EditToolStateTest {

    @BeforeEach
    void resetState() {
        EditToolState.getInstance().setCurrentTool(Tool.NAVIGATION);
        EditToolState.getInstance().clearSelection();
    }

    @Test
    void defaultToolIsNavigation() {
        assertEquals(Tool.NAVIGATION, EditToolState.getInstance().getCurrentTool());
    }

    @Test
    void setCurrentToolUpdatesState() {
        EditToolState.getInstance().setCurrentTool(Tool.SELECT);
        assertEquals(Tool.SELECT, EditToolState.getInstance().getCurrentTool());
    }

    @Test
    void selectionStartsEmpty() {
        assertTrue(EditToolState.getInstance().getSelectedNodes().isEmpty());
    }

    @Test
    void addSelectedNodeAddsToSelection() {
        UUID id = UUID.randomUUID();
        EditToolState.getInstance().addSelectedNode(id);
        assertEquals(1, EditToolState.getInstance().getSelectedNodes().size());
        assertTrue(EditToolState.getInstance().getSelectedNodes().contains(id));
    }

    @Test
    void addSelectedNodeDeduplicates() {
        UUID id = UUID.randomUUID();
        EditToolState.getInstance().addSelectedNode(id);
        EditToolState.getInstance().addSelectedNode(id);
        assertEquals(1, EditToolState.getInstance().getSelectedNodes().size());
    }

    @Test
    void clearSelectionRemovesAll() {
        EditToolState.getInstance().addSelectedNode(UUID.randomUUID());
        EditToolState.getInstance().addSelectedNode(UUID.randomUUID());
        EditToolState.getInstance().clearSelection();
        assertTrue(EditToolState.getInstance().getSelectedNodes().isEmpty());
    }
}
