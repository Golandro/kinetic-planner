package net.jsmua.kinetic_planner.gui.widgets;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link ConfigStepperRow#computeSteppedValue} 纯函数单元测试 - 步进器 clamp 逻辑。
 *
 * <p>覆盖 float 与 int 两个重载，验证域内步进、上限 clamp、下限 clamp 三种行为。
 * 该测试不依赖 MC，可在纯 JVM 运行。
 */
class ConfigStepperRowTest {

    // ===== float 重载 =====

    @Test
    void stepIncrement_withinRange() {
        assertEquals(1.05f,
            ConfigStepperRow.computeSteppedValue(1.0f, 0.05f, true, 0.1f, 10.0f),
            1e-6f);
    }

    @Test
    void stepIncrement_clampToMax() {
        assertEquals(10.0f,
            ConfigStepperRow.computeSteppedValue(9.98f, 0.05f, true, 0.1f, 10.0f),
            1e-6f);
    }

    @Test
    void stepDecrement_clampToMin() {
        assertEquals(0.1f,
            ConfigStepperRow.computeSteppedValue(0.12f, 0.05f, false, 0.1f, 10.0f),
            1e-6f);
    }

    @Test
    void stepIncrement_alphaClampToOne() {
        assertEquals(1.0f,
            ConfigStepperRow.computeSteppedValue(0.98f, 0.05f, true, 0.0f, 1.0f),
            1e-6f);
    }

    // ===== int 重载（priority） =====

    @Test
    void stepIncrement_priorityInteger() {
        assertEquals(5,
            ConfigStepperRow.computeSteppedValue(4, 1, true, 0, 100));
    }

    @Test
    void stepDecrement_priorityClampToZero() {
        assertEquals(0,
            ConfigStepperRow.computeSteppedValue(0, 1, false, 0, 100));
    }
}
