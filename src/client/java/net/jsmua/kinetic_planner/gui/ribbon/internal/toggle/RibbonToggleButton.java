package net.jsmua.kinetic_planner.gui.ribbon.internal.toggle;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Toggle;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.jsmua.kinetic_planner.gui.ribbon.api.ToolSize;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * 把 LDLib2 {@link Toggle} 封装为 Ribbon 风格的 button 样式 toggle。
 *
 * <p>封装细节（对上层隐藏）：
 * <ul>
 *   <li>隐藏 Toggle 自带的 markIcon（绿勾）</li>
 *   <li>隐藏 Toggle 自带的 toggleLabel</li>
 *   <li>把文本/图标设置到内部的 {@link Button} 上</li>
 *   <li>根据 {@link ToolSize} 选择水平（SMALL）或垂直（LARGE）布局</li>
 *   <li>激活态通过给内部 Button 添加/移除 {@code __on__} class 驱动 LSS</li>
 * </ul>
 *
 * <p>注意：Toggle 构造函数已对 toggleButton 调用 noText()，但 toggleLabel 默认显示。
 * 此处再次调用 {@code toggle.noText()} 确保 toggleLabel 隐藏。
 */
public final class RibbonToggleButton {

    private final Toggle toggle;
    private final Button button;
    private final ToolSize size;

    public RibbonToggleButton(Component text,
                              Optional<IGuiTexture> icon,
                              ToolSize size,
                              boolean initiallyActive) {
        this.toggle = new Toggle();
        this.button = toggle.toggleButton;
        this.size = size;

        // 隐藏 Toggle 自带的 label 和勾选标记
        toggle.noText();
        toggle.markIcon.setDisplay(false);

        // 内部 button 作为真实视觉按钮
        button.setText(text);
        icon.ifPresent(button::addPreIcon);
        button.addClass("kp-ribbon-tool");

        configureLayout(text);

        // 初始状态（不触发回调）
        toggle.setOn(initiallyActive, false);
        updateButtonStyle(initiallyActive);
    }

    /** 返回内部 Toggle，用于注册到 ToggleBasedRibbonToggleGroup。 */
    public Toggle asToggle() {
        return toggle;
    }

    /** 返回作为 UI 树的根元素（即 Toggle 本身）。 */
    public UIElement asElement() {
        return toggle;
    }

    public ToolSize getSize() {
        return size;
    }

    public boolean isOn() {
        return toggle.isOn();
    }

    public void setOn(boolean on) {
        toggle.setOn(on);
    }

    public void setToggleGroup(@Nullable RibbonToggleGroup group) {
        if (group != null) {
            group.register(this);
        }
    }

    public void setOnToggleChanged(Consumer<Boolean> callback) {
        toggle.setOnToggleChanged(active -> {
            updateButtonStyle(active);
            callback.accept(active);
        });
    }

    private void configureLayout(Component text) {
        if (size == ToolSize.LARGE) {
            // 2 列宽，垂直：图标按钮在上，文字标签在下
            toggle.layout(layout -> {
                layout.flexDirection(FlexDirection.COLUMN);
                layout.alignItems(AlignItems.CENTER);
                layout.width(40);
                layout.height(38);
                layout.paddingAll(1);
            });
            button.layout(layout -> {
                layout.width(32);
                layout.height(24);
            });
            // 大号工具的文本以独立 Label 放在 button 下方
            button.noText();
            var label = new Label();
            label.setText(text);
            label.addClass("kp-ribbon-tool-label");
            label.layout(layout -> {
                layout.height(10);
            });
            toggle.addChild(label);
        } else {
            // SMALL：水平排列，图标左文字右
            toggle.layout(layout -> {
                layout.flexDirection(FlexDirection.ROW);
                layout.alignItems(AlignItems.CENTER);
                layout.height(22);
                layout.paddingAll(1);
            });
            button.layout(layout -> {
                layout.height(18);
                layout.paddingHorizontal(4);
                layout.paddingVertical(1);
            });
        }
    }

    private void updateButtonStyle(boolean active) {
        if (active) {
            button.addClass("__on__");
        } else {
            button.removeClass("__on__");
        }
    }
}
