package net.jsmua.kinetic_planner.gui.ribbon.internal;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.jsmua.kinetic_planner.gui.ribbon.api.RibbonTabState;

/** FLOATING 模式浮层 (spec §9.2)。 */
public final class FloatingContentPanel extends UIElement {

    public static UIElement create(RibbonTabState tab) {
        var panel = new FloatingContentPanel();
        panel.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.top(20);
            layout.left(0);
            layout.widthPercent(100);
            layout.height(40);
        });
        // 失焦收起逻辑 + 工具组填充留运行时完善
        return panel;
    }
}
