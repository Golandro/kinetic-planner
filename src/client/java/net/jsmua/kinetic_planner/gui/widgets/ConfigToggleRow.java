package net.jsmua.kinetic_planner.gui.widgets;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Toggle;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * 配置面板 Toggle 行构建器 - label 左 + Toggle 右。
 *
 * <p>从 {@code KpConfigUIFactory} 提取，可在配置面板和未来编辑器面板复用。
 */
public final class ConfigToggleRow {

    private static final int LABEL_WIDTH = 90;
    private static final int TOGGLE_WIDTH = 22;

    private ConfigToggleRow() {}

    /**
     * 构建 toggle 配置行。
     *
     * @param label    行标题
     * @param initial  初始状态
     * @param disabled 是否禁用（熔断 provider）
     * @param callback 状态变化回调（参数为 new value）
     */
    public static UIElement create(String label, boolean initial, boolean disabled,
                                    Consumer<Boolean> callback) {
        var row = new UIElement();
        row.addClass("kp-config-row");
        // row 布局由 config.lss .kp-config-row 覆盖

        var labelEl = createRowLabel(label);

        var toggle = new Toggle();
        toggle.setOn(initial);
        toggle.setOnToggleChanged(callback::accept);
        toggle.noText();
        toggle.addClass("kp-toggle");
        toggle.layout(layout -> {
            layout.width(TOGGLE_WIDTH);
            layout.height(14);
        });
        if (disabled) {
            toggle.setActive(false);
        }

        row.addChild(labelEl);
        row.addChild(toggle);
        return row;
    }

    static Label createRowLabel(String text) {
        var label = new Label();
        label.setValue(Component.literal(text));
        label.layout(layout -> {
            layout.width(LABEL_WIDTH);
            layout.height(9);
        });
        return label;
    }
}
