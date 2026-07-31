package net.jsmua.kinetic_planner.gui.ribbon.internal.toggle;

import com.lowdragmc.lowdraglib2.gui.ui.elements.Toggle;

/**
 * 基于 LDLib2 {@link Toggle.ToggleGroup} 的 {@link RibbonToggleGroup} 实现。
 *
 * <p>继承 {@link Toggle.ToggleGroup}（而非委托），因为 {@code registerToggle} 是 protected
 * 方法。通过 {@link Toggle#setToggleGroup(Toggle.ToggleGroup)} 注册 toggle，LDLib2 内部会
 * 调用 {@code registerToggle} 并设置 {@code toggle.toggleGroup} 字段，完整触发互斥语义
 * （默认 allowEmpty=false，同组至少一个 toggle 保持激活）。
 */
public final class ToggleBasedRibbonToggleGroup extends Toggle.ToggleGroup implements RibbonToggleGroup {
    @Override
    public void register(RibbonToggleButton button) {
        // setToggleGroup 内部调用 this.registerToggle(toggle) 并设置 toggle.toggleGroup 字段
        button.asToggle().setToggleGroup(this);
    }
}
