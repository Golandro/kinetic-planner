package net.jsmua.kinetic_planner.gui.editor.ribbon;

import net.jsmua.kinetic_planner.gui.ribbon.api.RibbonCommand;

/**
 * 通用一次性命令 (demo: Clear/Invert/Action/Settings/Help 等非 toggle 按钮)。
 *
 * <p>包装 Runnable, execute() 委托给它。enabled=false 时按钮灰显 (演示 isEnabled)。
 */
final class RunnableCommand implements RibbonCommand {

    private final Runnable runnable;
    private final boolean enabled;

    RunnableCommand(Runnable runnable) {
        this(runnable, true);
    }

    RunnableCommand(Runnable runnable, boolean enabled) {
        this.runnable = runnable;
        this.enabled = enabled;
    }

    @Override
    public void execute() {
        runnable.run();
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
