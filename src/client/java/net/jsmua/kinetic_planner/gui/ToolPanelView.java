package net.jsmua.kinetic_planner.gui;

import com.lowdragmc.lowdraglib2.editor.ui.View;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.jsmua.kinetic_planner.gui.editor.EditToolState;
import net.jsmua.kinetic_planner.gui.editor.EditToolState.Tool;

/**
 * 工具面板 View（spec §6.5）。
 *
 * <p>左侧 View，含工具按钮列表（图标 + 名称）。选中工具时通知
 * {@link EditToolState#setCurrentTool(Tool)}。
 *
 * <p>按钮顺序对应 {@link Tool} 枚举的语义分组：
 * <ol>
 *   <li>Pan (V) → {@link Tool#NAVIGATION}</li>
 *   <li>Select (L) → {@link Tool#SELECT}</li>
 *   <li>Line (L) → {@link Tool#DRAW_LINE}</li>
 *   <li>Bezier (B) → {@link Tool#DRAW_BEZIER}</li>
 *   <li>Snap (S) → {@link Tool#SNAP}</li>
 * </ol>
 *
 * <p>注：按钮 label 字面量与快捷键映射沿用 brief Step 2；快捷键实际
 * 绑定在 Phase 4 后续 Task（{@code KpEditorScreen#keyPressed} 转发）。
 */
public class ToolPanelView extends View {

    public ToolPanelView() {
        super();
        layout(layout -> {
            layout.flexDirection(FlexDirection.COLUMN);
            layout.paddingAll(4);
            layout.gapAll(2);
        });
        addToolButton("Pan (V)", Tool.NAVIGATION);
        addToolButton("Select (L)", Tool.SELECT);
        addToolButton("Line (L)", Tool.DRAW_LINE);
        addToolButton("Bezier (B)", Tool.DRAW_BEZIER);
        addToolButton("Snap (S)", Tool.SNAP);
    }

    private void addToolButton(String label, Tool tool) {
        Button btn = new Button();
        btn.setText(label);
        btn.setOnClick(event -> EditToolState.getInstance().setCurrentTool(tool));
        addChild(btn);
    }
}
