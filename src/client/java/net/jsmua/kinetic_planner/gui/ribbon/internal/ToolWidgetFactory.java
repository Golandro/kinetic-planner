package net.jsmua.kinetic_planner.gui.ribbon.internal;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import net.jsmua.kinetic_planner.gui.ribbon.api.ButtonAction;
import net.jsmua.kinetic_planner.gui.ribbon.api.RibbonToolDefinition;
import net.jsmua.kinetic_planner.gui.ribbon.api.SeparatorAction;
import net.jsmua.kinetic_planner.gui.ribbon.api.ToolAction;

/** ToolAction -> UIElement 工厂 (spec §4.1)。Phase 1: ButtonAction + SeparatorAction。 */
public final class ToolWidgetFactory {

    public static UIElement create(RibbonToolDefinition tool) {
        ToolAction action = tool.getAction();
        if (action instanceof ButtonAction btn) {
            var button = new Button();
            button.setText(tool.getDisplayName());
            button.setOnClick(event -> btn.command().execute());
            // toggle 视觉反馈 + disabled 灰显留 Phase 2+ 完善
            return button;
        } else if (action instanceof SeparatorAction) {
            var sep = new UIElement();
            sep.addClass("kp-ribbon-separator");
            return sep;
        }
        throw new IllegalStateException("Unknown ToolAction: " + action.getClass());
    }
}
