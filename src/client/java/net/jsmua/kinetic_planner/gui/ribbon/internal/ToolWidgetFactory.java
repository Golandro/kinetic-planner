package net.jsmua.kinetic_planner.gui.ribbon.internal;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import net.jsmua.kinetic_planner.gui.ribbon.api.ButtonAction;
import net.jsmua.kinetic_planner.gui.ribbon.api.RibbonToolDefinition;
import net.jsmua.kinetic_planner.gui.ribbon.api.RibbonToggleCommand;
import net.jsmua.kinetic_planner.gui.ribbon.api.SeparatorAction;
import net.jsmua.kinetic_planner.gui.ribbon.api.ToolAction;
import net.jsmua.kinetic_planner.gui.ribbon.api.ToolSize;
import net.jsmua.kinetic_planner.gui.ribbon.internal.toggle.RibbonToggleButton;
import net.jsmua.kinetic_planner.gui.ribbon.internal.toggle.RibbonToggleGroup;
import org.jetbrains.annotations.Nullable;

/**
 * ToolAction -> UIElement 工厂 (spec §4.1)。
 *
 * <p>支持三种 ToolAction:
 * <ul>
 *   <li>{@link ButtonAction} with toggle=false -> {@link Button}</li>
 *   <li>{@link ButtonAction} with toggle=true  -> {@link RibbonToggleButton} (封装 LDLib2 Toggle,
 *       可选 {@link RibbonToggleGroup} 互斥组)</li>
 *   <li>{@link SeparatorAction} -> 纯 UIElement + {@code kp-ribbon-separator} class</li>
 * </ul>
 *
 * <p>所有生成控件附带 {@code kp-ribbon-tool} class 以便 LSS 统一调样式。
 */
public final class ToolWidgetFactory {

    private ToolWidgetFactory() {}

    /**
     * 创建工具控件。
     *
     * @param tool  工具定义 (含 action)
     * @param group toggle 互斥组; 仅对 toggle=true 工具生效, 可为 null
     * @return 渲染节点 (Button / Toggle (via RibbonToggleButton) / UIElement)
     */
    public static UIElement create(RibbonToolDefinition tool,
                                   @Nullable RibbonToggleGroup group) {
        ToolAction action = tool.getAction();
        if (action instanceof ButtonAction btn) {
            if (btn.toggle()) {
                return createToggle(tool, btn, group);
            }
            return createButton(tool, btn);
        } else if (action instanceof SeparatorAction) {
            var sep = new UIElement();
            sep.addClass("kp-ribbon-separator");
            return sep;
        }
        throw new IllegalStateException("Unknown ToolAction: " + action.getClass());
    }

    /** 旧签名保留: 不带 ToggleGroup, 默认 null。 */
    public static UIElement create(RibbonToolDefinition tool) {
        return create(tool, null);
    }

    private static UIElement createButton(RibbonToolDefinition tool, ButtonAction btn) {
        var button = new Button();
        button.setText(tool.getDisplayName());
        tool.getIcon().ifPresent(button::addPreIcon);
        button.setOnClick(event -> btn.command().execute());
        button.addClass("kp-ribbon-tool");
        applySizeLayout(button, tool.getSize());
        return button;
    }

    private static UIElement createToggle(RibbonToolDefinition tool,
                                          ButtonAction btn,
                                          @Nullable RibbonToggleGroup group) {
        if (!(btn.command() instanceof RibbonToggleCommand toggleCmd)) {
            throw new IllegalStateException("ButtonAction.toggle=true 要求 command 为 RibbonToggleCommand, 实际: "
                + btn.command().getClass());
        }
        var wrapper = new RibbonToggleButton(
            tool.getDisplayName(),
            tool.getIcon(),
            tool.getSize(),
            toggleCmd.isActive()
        );
        wrapper.setToggleGroup(group);
        wrapper.setOnToggleChanged(toggleCmd::setActive);
        return wrapper.asElement();
    }

    private static void applySizeLayout(Button button, ToolSize size) {
        if (size == ToolSize.LARGE) {
            button.layout(layout -> {
                layout.width(32);
                layout.height(32);
            });
        } else {
            button.layout(layout -> {
                layout.height(18);
                layout.paddingHorizontal(4);
            });
        }
    }
}
