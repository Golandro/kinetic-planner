package net.jsmua.kinetic_planner.gui.ribbon.internal;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import net.jsmua.kinetic_planner.gui.ribbon.api.ButtonAction;
import net.jsmua.kinetic_planner.gui.ribbon.api.RibbonCommand;
import net.jsmua.kinetic_planner.gui.ribbon.api.RibbonToggleCommand;
import net.jsmua.kinetic_planner.gui.ribbon.api.SeparatorAction;
import net.jsmua.kinetic_planner.gui.ribbon.api.SimpleRibbonToolDefinition;
import net.jsmua.kinetic_planner.gui.ribbon.api.TextTooltip;
import net.jsmua.kinetic_planner.gui.ribbon.api.ToolSize;
import net.jsmua.kinetic_planner.gui.ribbon.internal.toggle.RibbonToggleGroup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ToolWidgetFactory} 工厂行为测试。
 *
 * <p><b>测试环境限制:</b> {@code new Button()} / {@code new Toggle()} 触发
 * {@code UIElement.<clinit>} → {@code LDLib2Registries.<clinit>} → {@code AutoRegistry.autoRegister}
 * → {@code ModList.get().getAllScanData()}。纯 JVM 测试无 NeoForge 运行时,
 * {@code ModList.get()} 返回 null, 导致 {@code ExceptionInInitializerError}。
 * 三个直接构造的用例 {@code @Disabled}, 靠运行时验收 (spec §13 LDLib2 clinit 陷阱,
 * 同 {@code KpMapEditorTest} 模式)。
 *
 * <p>纯逻辑用例 (separator 不依赖 LDLib2 clinit 之外的资源) 通过反射验证类层次。
 */
class ToolWidgetFactoryTest {

    private static RibbonCommand noopCommand() {
        return new RibbonCommand() {
            @Override public void execute() {}
            @Override public boolean isEnabled() { return true; }
        };
    }

    private static RibbonToggleCommand noopToggle() {
        return new RibbonToggleCommand() {
            @Override public boolean isActive() { return false; }
            @Override public void setActive(boolean active) {}
            @Override public void execute() {}
            @Override public boolean isEnabled() { return true; }
        };
    }

    private static SimpleRibbonToolDefinition buttonTool(String path, String name, boolean toggle) {
        RibbonCommand cmd = toggle ? noopToggle() : noopCommand();
        return new SimpleRibbonToolDefinition(
            ResourceLocation.fromNamespaceAndPath("kp", path),
            Component.literal(name),
            Optional.empty(),
            Optional.of(new TextTooltip(Component.literal(name))),
            Optional.empty(),
            ToolSize.SMALL,
            new ButtonAction(cmd, toggle)
        );
    }

    private static SimpleRibbonToolDefinition separatorTool(String path) {
        return new SimpleRibbonToolDefinition(
            ResourceLocation.fromNamespaceAndPath("kp", path),
            Component.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            ToolSize.SMALL,
            new SeparatorAction()
        );
    }

    /**
     * 验证 plain ButtonAction -> Button (运行时验收).
     */
    @Test
    @Disabled("UIElement.<clinit> 需要 NeoForge 运行时; 靠 gradlew runClient 验收")
    void plainButtonActionReturnsButton() {
        var tool = buttonTool("btn_plain", "Plain", false);
        var widget = ToolWidgetFactory.create(tool, null);
        assertInstanceOf(Button.class, widget, "非 toggle ButtonAction 必须返回 Button");
    }

    /**
     * 验证 toggle ButtonAction -> Toggle (运行时验收).
     */
    @Test
    @Disabled("UIElement.<clinit> 需要 NeoForge 运行时; 靠 gradlew runClient 验收")
    void toggleButtonActionReturnsToggle() {
        var tool = buttonTool("btn_toggle", "Toggle", true);
        var widget = ToolWidgetFactory.create(tool, null);
        // RibbonToggleButton 封装了 Toggle, asElement() 返回 Toggle 实例
        assertInstanceOf(com.lowdragmc.lowdraglib2.gui.ui.elements.Toggle.class, widget,
            "toggle ButtonAction 必须返回 Toggle (via RibbonToggleButton)");
    }

    /**
     * 验证 SeparatorAction -> UIElement + .kp-ribbon-separator class (运行时验收).
     */
    @Test
    @Disabled("UIElement.<clinit> 需要 NeoForge 运行时; 靠 gradlew runClient 验收")
    void separatorActionReturnsSeparatorElement() {
        var tool = separatorTool("sep1");
        var widget = ToolWidgetFactory.create(tool, null);
        assertInstanceOf(UIElement.class, widget);
        assertTrue(widget.getClasses().contains("kp-ribbon-separator"),
            "Separator 必须带 kp-ribbon-separator class");
    }

    /**
     * 验证 ToolWidgetFactory 静态方法存在且可访问 (不触发 clinit)。
     */
    @Test
    void createMethodSignatureAcceptsNullableToggleGroup() throws NoSuchMethodException {
        var m = ToolWidgetFactory.class.getMethod("create",
            net.jsmua.kinetic_planner.gui.ribbon.api.RibbonToolDefinition.class,
            RibbonToggleGroup.class);
        assertEquals(UIElement.class, m.getReturnType(),
            "create 必须返回 UIElement");
    }
}
