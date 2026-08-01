package net.jsmua.kinetic_planner.gui.widgets;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventListener;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * 配置面板步进器行构建器 - label + [−] value [+]。
 *
 * <p>从 {@code KpConfigUIFactory} 提取，支持 int 和 float 两种值类型。
 * 包含纯函数 {@link #computeSteppedValue} 供单测验证 clamp 逻辑。
 */
public final class ConfigStepperRow {

    private static final int LABEL_WIDTH = 90;
    private static final int STEPPER_WIDTH = 64;

    private ConfigStepperRow() {}

    /**
     * 步进器数值计算 + clamp 纯函数（float 重载）。
     */
    public static float computeSteppedValue(float current, float step, boolean increment,
                                             float min, float max) {
        float next = increment ? current + step : current - step;
        return Math.max(min, Math.min(max, next));
    }

    /**
     * 步进器数值计算 + clamp 纯函数（int 重载，用于 priority）。
     */
    public static int computeSteppedValue(int current, int step, boolean increment,
                                           int min, int max) {
        int next = increment ? current + step : current - step;
        return Math.max(min, Math.min(max, next));
    }

    /**
     * 构建 int 步进器行：label + [−] value [+]。
     */
    public static UIElement createInt(String label, int initial, int step,
                                       int min, int max, boolean disabled,
                                       Consumer<Integer> callback) {
        var row = createRowSkeleton(label);
        var stepper = createStepperContainer();

        var holder = new int[]{initial};
        var valueLabel = createValueLabel(String.valueOf(initial));

        stepper.addChild(createStepperButton("−", disabled, event -> {
            int next = computeSteppedValue(holder[0], step, false, min, max);
            holder[0] = next;
            valueLabel.setValue(Component.literal(String.valueOf(next)));
            callback.accept(next);
        }));
        stepper.addChild(valueLabel);
        stepper.addChild(createStepperButton("+", disabled, event -> {
            int next = computeSteppedValue(holder[0], step, true, min, max);
            holder[0] = next;
            valueLabel.setValue(Component.literal(String.valueOf(next)));
            callback.accept(next);
        }));

        row.addChild(stepper);
        return row;
    }

    /**
     * 构建 float 步进器行：label + [−] value [+]。显示格式 {@code %.2f}。
     */
    public static UIElement createFloat(String label, float initial, float step,
                                         float min, float max, boolean disabled,
                                         Consumer<Float> callback) {
        var row = createRowSkeleton(label);
        var stepper = createStepperContainer();

        var holder = new float[]{initial};
        var valueLabel = createValueLabel(String.format("%.2f", initial));

        stepper.addChild(createStepperButton("−", disabled, event -> {
            float next = computeSteppedValue(holder[0], step, false, min, max);
            holder[0] = next;
            valueLabel.setValue(Component.literal(String.format("%.2f", next)));
            callback.accept(next);
        }));
        stepper.addChild(valueLabel);
        stepper.addChild(createStepperButton("+", disabled, event -> {
            float next = computeSteppedValue(holder[0], step, true, min, max);
            holder[0] = next;
            valueLabel.setValue(Component.literal(String.format("%.2f", next)));
            callback.accept(next);
        }));

        row.addChild(stepper);
        return row;
    }

    private static UIElement createRowSkeleton(String labelText) {
        var row = new UIElement();
        row.addClass("kp-config-row");
        // row 布局由 config.lss .kp-config-row 覆盖
        row.addChild(ConfigToggleRow.createRowLabel(labelText));
        return row;
    }

    private static UIElement createStepperContainer() {
        var stepper = new UIElement();
        stepper.addClass("kp-stepper");
        stepper.layout(layout -> layout.width(STEPPER_WIDTH));
        return stepper;
    }

    private static Label createValueLabel(String initialText) {
        var valueLabel = new Label();
        valueLabel.setValue(Component.literal(initialText));
        valueLabel.layout(layout -> {
            layout.width(24);
            layout.height(9);
        });
        valueLabel.textStyle(style -> style.textAlignHorizontal(
            com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal.CENTER));
        return valueLabel;
    }

    private static Button createStepperButton(String text, boolean disabled, UIEventListener onClick) {
        var button = new Button();
        button.setText(text);
        button.setOnClick(onClick);
        button.layout(layout -> {
            layout.width(14);
            layout.height(14);
        });
        if (disabled) {
            button.setActive(false);
        }
        return button;
    }
}
