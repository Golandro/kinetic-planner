package net.jsmua.kinetic_planner.gui.ribbon.api;

/**
 * 命令回调接口 (spec §4.3)。
 *
 * <p>作为命令树的 UI 适配层, 不直接替代 KpCommandHandlers。
 *
 * <p><b>测试 seam 要求:</b> 实现禁止调用 getInstance() 静态单例; 通过构造函数注入所需依赖。
 */
public interface RibbonCommand {
    /** 执行命令。 */
    void execute();

    /** 是否启用。返回 false 时按钮灰显。 */
    default boolean isEnabled() { return true; }
}
