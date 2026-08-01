package net.jsmua.kinetic_planner.gui;

import com.lowdragmc.lowdraglib2.editor.ui.View;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.jsmua.kinetic_planner.gui.editor.EditToolState;

/**
 * 工具面板 View（spec §6.5）。
 *
 * <p>左侧 View，含工具按钮列表（图标 + 名称）。选中工具时通知
 * {@link EditToolState#setCurrentTool}。
 *
 * <p>按钮从 {@link EditToolState.Tool} 枚举遍历生成（审计 R2 修复，消除散弹式修改）。
 */
public class ToolPanelView extends View {

    public ToolPanelView() {
        super();
        addClass("kp-tool-panel");
        layout(layout -> {
            layout.flexDirection(FlexDirection.COLUMN);
            layout.paddingAll(4);
            layout.gapAll(2);
        });
        // 从 Tool 枚举遍历生成按钮（审计 R2 修复，消除散弹式修改 + 标签-按键不一致 bug）
        for (var tool : EditToolState.Tool.values()) {
            String label = tool.getDisplayName() + " (" + tool.getKeyLabel() + ")";
            addToolButton(label, tool);
        }
    }

    private void addToolButton(String label, EditToolState.Tool tool) {
        Button btn = new Button();
        btn.addClass("kp-tool-button");
        btn.setText(label);
        btn.setOnClick(event -> EditToolState.getInstance().setCurrentTool(tool));
        addChild(btn);
    }
}
