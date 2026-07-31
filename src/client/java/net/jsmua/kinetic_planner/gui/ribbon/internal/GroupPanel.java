package net.jsmua.kinetic_planner.gui.ribbon.internal;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.jsmua.kinetic_planner.gui.ribbon.api.RibbonToolGroupDefinition;

/** 工具组面板渲染 (spec §5.2)。 */
public final class GroupPanel extends UIElement {

    public static UIElement build(RibbonToolGroupDefinition group) {
        var panel = new GroupPanel();
        panel.layout(layout -> {
            layout.flexDirection(FlexDirection.COLUMN);
        });
        group.getTools().forEach(tool -> {
            panel.addChild(ToolWidgetFactory.create(tool));
        });
        return panel;
    }
}
