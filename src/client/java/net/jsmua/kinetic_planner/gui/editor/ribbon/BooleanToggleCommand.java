package net.jsmua.kinetic_planner.gui.editor.ribbon;

import net.jsmua.kinetic_planner.gui.ribbon.api.RibbonToggleCommand;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 通用布尔开关命令 (demo: 绑定 IKPConfig 布尔属性 / 静态标志)。
 *
 * <p>构造注入 getter/setter 函数对, 不调静态单例, 保障可测性。
 * execute() 翻转当前值; setActive(b) 直接设置; isActive() 查询当前值。
 *
 * <p>用于 View tab 的图层/标签/线宽开关, 以及上下文选项卡组激活开关。
 */
final class BooleanToggleCommand implements RibbonToggleCommand {

    private final Supplier<Boolean> getter;
    private final Consumer<Boolean> setter;

    BooleanToggleCommand(Supplier<Boolean> getter, Consumer<Boolean> setter) {
        this.getter = getter;
        this.setter = setter;
    }

    @Override
    public void execute() {
        setter.accept(!getter.get());
    }

    @Override
    public boolean isActive() {
        return getter.get();
    }

    @Override
    public void setActive(boolean active) {
        setter.accept(active);
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
