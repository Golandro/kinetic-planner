package net.jsmua.kinetic_planner.gui.editor.ribbon;

import net.jsmua.kinetic_planner.gui.editor.EditToolState;
import net.jsmua.kinetic_planner.gui.ribbon.api.ButtonAction;
import net.jsmua.kinetic_planner.gui.ribbon.api.RibbonToolDefinition;
import net.jsmua.kinetic_planner.gui.ribbon.api.SimpleRibbonToolDefinition;
import net.jsmua.kinetic_planner.gui.ribbon.api.TextTooltip;
import net.jsmua.kinetic_planner.gui.ribbon.api.ToolSize;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/**
 * Tool 枚举 -> RibbonToolDefinition 集中映射 (spec §4.8, DRY)。
 *
 * <p>所有 KP 工具 definition 在此集中创建, 避免散落在多处。
 * ToolToggleCommand 通过构造注入 EditToolState, 不调 getInstance()。
 */
public final class KpToolDefinitions {

    /** Tools tab 工具互斥组 ID：Pan/Select/Line/Bezier/Snap 五选一。 */
    public static final ResourceLocation TOOLS_MUTEX_ID =
        ResourceLocation.fromNamespaceAndPath("kp", "mutex_tools");

    private final EditToolState toolState;

    public KpToolDefinitions(EditToolState toolState) {
        this.toolState = toolState;
    }

    public RibbonToolDefinition panTool() {
        return makeTool("tool_pan", "Pan", EditToolState.Tool.NAVIGATION, "P");
    }

    public RibbonToolDefinition selectTool() {
        return makeTool("tool_select", "Select", EditToolState.Tool.SELECT, "V");
    }

    public RibbonToolDefinition lineTool() {
        return makeTool("tool_line", "Line", EditToolState.Tool.DRAW_LINE, "L");
    }

    public RibbonToolDefinition bezierTool() {
        return makeTool("tool_bezier", "Bezier", EditToolState.Tool.DRAW_BEZIER, "B");
    }

    public RibbonToolDefinition snapTool() {
        return makeTool("tool_snap", "Snap", EditToolState.Tool.SNAP, "S");
    }

    public List<RibbonToolDefinition> allTools() {
        return List.of(panTool(), selectTool(), lineTool(), bezierTool(), snapTool());
    }

    private RibbonToolDefinition makeTool(String path, String name, EditToolState.Tool tool, String shortcut) {
        return new SimpleRibbonToolDefinition(
            ResourceLocation.fromNamespaceAndPath("kp", path),
            Component.literal(name),
            Optional.empty(),  // icon 留运行时补充
            Optional.of(new TextTooltip(Component.literal(name + " (" + shortcut + ")"))),
            Optional.of(shortcut),
            ToolSize.LARGE,
            new ButtonAction(new ToolToggleCommand(toolState, tool), true)
        );
    }
}
