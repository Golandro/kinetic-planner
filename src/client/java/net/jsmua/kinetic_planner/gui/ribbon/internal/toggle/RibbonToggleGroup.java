package net.jsmua.kinetic_planner.gui.ribbon.internal.toggle;

/**
 * 工具互斥组抽象（internal/toggle 层）。
 *
 * <p>仅由渲染层（ToolWidgetFactory / GroupPanel / RibbonBuilder / RibbonToggleButton）消费。
 * Collection 层不依赖此接口，只通过
 * {@link net.jsmua.kinetic_planner.gui.ribbon.api.RibbonToolGroupDefinition#getMutualExclusionGroupId()}
 * 声明互斥关系。
 *
 * <p>放在 internal/toggle 而非 api 包：避免 api -> internal/toggle -> api 包级循环依赖
 * （RibbonToggleButton 依赖 LDLib2，不应被 api 层传递引用）。
 */
public interface RibbonToggleGroup {
    /** 注册一个 button 样式 toggle；由渲染层在构造时调用。 */
    void register(RibbonToggleButton button);
}
