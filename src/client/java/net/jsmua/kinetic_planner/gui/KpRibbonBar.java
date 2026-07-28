package net.jsmua.kinetic_planner.gui;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.jsmua.kinetic_planner.gui.editor.EditToolState;
import net.jsmua.kinetic_planner.gui.editor.EditToolState.Tool;
import net.minecraft.network.chat.Component;

/**
 * Ribbon 菜单栏（spec §6.4）。
 *
 * <p>结构上自包含：工具与命令集中在 Ribbon，不依赖独立菜单栏 + 工具栏的分离结构。
 * 配置面板以弹出抽屉形式从 Ribbon 触发（Phase 后续实现）。
 *
 * <p>结构：
 * <pre>
 * KpRibbonBar (UIElement, width=100%, height=~24px, flexDirection=ROW)
 * ├── Tab Group: "File" (New/Open/Save)
 * ├── Tab Group: "Tools" (Select/Line/Bezier/Pan)
 * ├── Tab Group: "View" (Toggle Overlay/Theme)
 * ├── Spacer (flex=1)
 * └── Button: Settings (齿轮)
 * </pre>
 */
public class KpRibbonBar extends UIElement {

    public KpRibbonBar() {
        super();
        addClass("kp-ribbon");
        layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.widthPercent(100);
            layout.height(24);
            layout.paddingHorizontal(4);
            layout.gapAll(4);
        });

        // File 组（占位，P4 阶段实现具体逻辑）
        addGroupLabel("File");
        addButton("New", () -> {});
        addButton("Open", () -> {});
        addButton("Save", () -> {});

        // Tools 组
        addGroupLabel("Tools");
        addButton("Select", () -> EditToolState.getInstance().setCurrentTool(Tool.SELECT));
        addButton("Line", () -> EditToolState.getInstance().setCurrentTool(Tool.DRAW_LINE));
        addButton("Bezier", () -> EditToolState.getInstance().setCurrentTool(Tool.DRAW_BEZIER));
        addButton("Pan", () -> EditToolState.getInstance().setCurrentTool(Tool.NAVIGATION));

        // View 组
        addGroupLabel("View");
        addButton("Overlay", () -> {});
        addButton("Theme", () -> {});

        // 弹性间隔（推 Settings 到右）
        var spacer = new UIElement();
        spacer.layout(layout -> layout.flexGrow(1));
        addChild(spacer);

        // Settings 按钮（齿轮，弹出配置抽屉，Phase 后续实现）
        addButton("⚙", () -> {});
    }

    private void addGroupLabel(String text) {
        var label = new com.lowdragmc.lowdraglib2.gui.ui.elements.Label();
        label.setValue(Component.literal(text));
        label.addClass("kp-ribbon-group-label");
        addChild(label);
    }

    private void addButton(String text, Runnable onClick) {
        var btn = new Button();
        btn.setText(text);
        btn.setOnClick(event -> onClick.run());
        addChild(btn);
    }
}
