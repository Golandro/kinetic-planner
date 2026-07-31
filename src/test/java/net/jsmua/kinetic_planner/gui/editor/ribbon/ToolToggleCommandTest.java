package net.jsmua.kinetic_planner.gui.editor.ribbon;

import net.jsmua.kinetic_planner.gui.editor.EditToolState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ToolToggleCommandTest {

    @Test
    void executeCallsInjectedToolStateSetCurrentTool() {
        var mockState = mock(EditToolState.class);
        var cmd = new ToolToggleCommand(mockState, EditToolState.Tool.NAVIGATION);

        cmd.execute();

        verify(mockState).setCurrentTool(EditToolState.Tool.NAVIGATION);
    }

    @Test
    void isActiveQueriesInjectedToolState() {
        var mockState = mock(EditToolState.class);
        when(mockState.getCurrentTool()).thenReturn(EditToolState.Tool.SELECT);
        var cmd = new ToolToggleCommand(mockState, EditToolState.Tool.SELECT);

        assertTrue(cmd.isActive());

        when(mockState.getCurrentTool()).thenReturn(EditToolState.Tool.NAVIGATION);
        assertFalse(cmd.isActive());
    }

    @Test
    void setActiveTrueCallsExecute() {
        var mockState = mock(EditToolState.class);
        var cmd = new ToolToggleCommand(mockState, EditToolState.Tool.DRAW_LINE);

        cmd.setActive(true);

        verify(mockState).setCurrentTool(EditToolState.Tool.DRAW_LINE);
    }

    @Test
    void setActiveFalseDoesNothing() {
        var mockState = mock(EditToolState.class);
        var cmd = new ToolToggleCommand(mockState, EditToolState.Tool.SNAP);

        cmd.setActive(false);

        verifyNoInteractions(mockState);
    }

    @Test
    void isEnabledAlwaysTrue() {
        var mockState = mock(EditToolState.class);
        var cmd = new ToolToggleCommand(mockState, EditToolState.Tool.NAVIGATION);
        assertTrue(cmd.isEnabled());
    }
}
