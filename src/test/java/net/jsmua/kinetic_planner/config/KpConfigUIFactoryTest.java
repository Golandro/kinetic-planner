package net.jsmua.kinetic_planner.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link KpConfigUIFactory#computeSteppedValue} 纯函数单元测试 - 步进器 clamp 逻辑。
 *
 * <p>spec §10.1 + §10.2 定义。覆盖 float 与 int 两个重载，
 * 验证域内步进、上限 clamp、下限 clamp 三种行为。
 *
 * <p>该测试不依赖 MC，可在纯 JVM 运行。
 */
class KpConfigUIFactoryTest {

    // ===== float 重载 =====

    @Test
    void stepIncrement_withinRange() {
        assertEquals(1.05f,
            KpConfigUIFactory.computeSteppedValue(1.0f, 0.05f, true, 0.1f, 10.0f),
            1e-6f);
    }

    @Test
    void stepIncrement_clampToMax() {
        assertEquals(10.0f,
            KpConfigUIFactory.computeSteppedValue(9.98f, 0.05f, true, 0.1f, 10.0f),
            1e-6f);
    }

    @Test
    void stepDecrement_clampToMin() {
        assertEquals(0.1f,
            KpConfigUIFactory.computeSteppedValue(0.12f, 0.05f, false, 0.1f, 10.0f),
            1e-6f);
    }

    @Test
    void stepIncrement_alphaClampToOne() {
        assertEquals(1.0f,
            KpConfigUIFactory.computeSteppedValue(0.98f, 0.05f, true, 0.0f, 1.0f),
            1e-6f);
    }

    // ===== int 重载（priority） =====

    @Test
    void stepIncrement_priorityInteger() {
        assertEquals(5,
            KpConfigUIFactory.computeSteppedValue(4, 1, true, 0, 100));
    }

    @Test
    void stepDecrement_priorityClampToZero() {
        assertEquals(0,
            KpConfigUIFactory.computeSteppedValue(0, 1, false, 0, 100));
    }
}
