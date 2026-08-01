package net.jsmua.kinetic_planner.gui.ribbon.internal;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.jsmua.kinetic_planner.gui.ribbon.api.RibbonToolGroupDefinition;
import net.jsmua.kinetic_planner.gui.ribbon.internal.toggle.RibbonToggleGroup;
import org.jetbrains.annotations.Nullable;

/**
 * 工具组面板 (spec §5.2)。
 *
 * <p>布局: GroupPanel 自身为 COLUMN 外壳, 内含一个 ROW 工具行 + 可选底部 Label。
 * <pre>
 * GroupPanel (COLUMN, kp-ribbon-group)
 * ├── UIElement (ROW, kp-ribbon-tool-row): 工具按钮横向排列
 * └── Label (kp-ribbon-group-label, 可选): 组底部标注
 * </pre>
 */
public final class GroupPanel extends UIElement {

    private GroupPanel() {
        super();
        addClass("kp-ribbon-group");
        layout(layout -> layout.flexDirection(FlexDirection.COLUMN));
    }

    /**
     * 构建工具组面板。
     *
     * @param group 工具组定义
     * @param toggleGroup 互斥 toggle 组; 透传给 {@link ToolWidgetFactory}, 仅对 toggle 工具生效, 可为 null
     * @return 渲染节点 (GroupPanel)
     */
    public static UIElement build(RibbonToolGroupDefinition group,
                                  @Nullable RibbonToggleGroup toggleGroup) {
        var panel = new GroupPanel();

        var toolRow = new UIElement();
        toolRow.addClass("kp-ribbon-tool-row");
        toolRow.layout(layout -> layout.flexDirection(FlexDirection.ROW));
        for (var tool : group.getTools()) {
            toolRow.addChild(ToolWidgetFactory.create(tool, toggleGroup));
        }
        panel.addChild(toolRow);

        group.getDisplayName().ifPresent(name -> {
            var label = new Label();
            label.setText(name);
            label.addClass("kp-ribbon-group-label");
            panel.addChild(label);
        });

        return panel;
    }

    /** 旧签名保留: 不带 ToggleGroup, 默认 null。 */
    public static UIElement build(RibbonToolGroupDefinition group) {
        return build(group, null);
    }
}
