package net.jsmua.kinetic_planner.gui.ribbon;

/**
 * 框架公共常量 (spec §5.1)。
 *
 * <p>集中默认值, 避免在 RibbonToolDefinition / RibbonToolGroupDefinition 等多处重复字面量 (DRY)。
 */
public final class RibbonConstants {

    /** 默认溢出权重: 数值越大越先被缩窄/隐藏。 */
    public static final int DEFAULT_OVERFLOW_WEIGHT = 100;

    private RibbonConstants() {}
}
