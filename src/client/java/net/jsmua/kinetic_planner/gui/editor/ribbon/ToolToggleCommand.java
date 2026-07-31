package net.jsmua.kinetic_planner.gui.editor.ribbon;

import net.jsmua.kinetic_planner.gui.editor.EditToolState;
import net.jsmua.kinetic_planner.gui.ribbon.api.RibbonToggleCommand;

/**
 * RibbonToggleCommand 适配 EditToolState (spec §4.8)。
 *
 * <p><b>关键约束:</b> 构造注入 EditToolState, 不调 EditToolState.getInstance()。
 * 保证 ToolToggleCommandTest 可用 Mockito mock EditToolState 验证行为。
 */
final class ToolToggleCommand implements RibbonToggleCommand {

    private final EditToolState toolState;
    private final EditToolState.Tool tool;

    ToolToggleCommand(EditToolState toolState, EditToolState.Tool tool) {
        this.toolState = toolState;
        this.tool = tool;
    }

    @Override
    public void execute() {
        toolState.setCurrentTool(tool);
    }

    @Override
    public boolean isActive() {
        return toolState.getCurrentTool() == tool;
    }

    @Override
    public void setActive(boolean active) {
        if (active) execute();
        // setActive(false) 不做任何事 (工具切换是单选, 设为 false 无意义)
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
