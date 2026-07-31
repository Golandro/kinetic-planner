package net.jsmua.kinetic_planner.gui.ribbon.internal.toggle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ToggleBasedRibbonToggleGroup} 反射签名测试。
 *
 * <p>纯 JVM 测试：验证类层次与方法签名，不触发 LDLib2 clinit。
 */
class ToggleBasedRibbonToggleGroupTest {

    @Test
    void implementsRibbonToggleGroup() {
        assertTrue(RibbonToggleGroup.class.isAssignableFrom(ToggleBasedRibbonToggleGroup.class),
            "ToggleBasedRibbonToggleGroup 必须实现 RibbonToggleGroup");
    }

    @Test
    void registerMethodSignatureMatchesInterface() throws NoSuchMethodException {
        var interfaceMethod = RibbonToggleGroup.class.getMethod("register", RibbonToggleButton.class);
        var implMethod = ToggleBasedRibbonToggleGroup.class.getMethod("register", RibbonToggleButton.class);
        assertEquals(interfaceMethod.getReturnType(), implMethod.getReturnType());
    }
}
