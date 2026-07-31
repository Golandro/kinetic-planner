package net.jsmua.kinetic_planner.gui.ribbon.internal.toggle;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Toggle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link RibbonToggleButton} 反射签名测试。
 *
 * <p>纯 JVM 测试：验证类层次与方法签名，不触发 LDLib2 clinit。
 * 运行时行为靠 {@code gradlew runClient} 验收。
 */
class RibbonToggleButtonTest {

    @Test
    @org.junit.jupiter.api.Disabled("UIElement.<clinit> 需要 NeoForge 运行时; 靠 gradlew runClient 验收")
    void exposesToggleAndElement() {
        var btn = new RibbonToggleButton(
            net.minecraft.network.chat.Component.literal("Test"),
            java.util.Optional.empty(),
            net.jsmua.kinetic_planner.gui.ribbon.api.ToolSize.SMALL,
            false
        );
        assertInstanceOf(Toggle.class, btn.asToggle());
        assertInstanceOf(UIElement.class, btn.asElement());
    }

    @Test
    void classExistsAndHasRequiredMethods() throws NoSuchMethodException {
        var clazz = RibbonToggleButton.class;
        clazz.getMethod("asToggle");
        clazz.getMethod("asElement");
        clazz.getMethod("setOnToggleChanged", java.util.function.Consumer.class);
    }
}
