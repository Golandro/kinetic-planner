package net.jsmua.kinetic_planner.gui.ribbon;

import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import net.jsmua.kinetic_planner.gui.ribbon.api.ButtonAction;
import net.jsmua.kinetic_planner.gui.ribbon.api.RibbonCommand;
import net.jsmua.kinetic_planner.gui.ribbon.api.SimpleRibbonToolDefinition;
import net.jsmua.kinetic_planner.gui.ribbon.api.ToolSize;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link QatBar} 渲染行为测试。
 *
 * <p><b>测试环境限制:</b> 同 {@link ToolWidgetFactoryTest}, 直接构造触发 UIElement.<clinit>。
 * 运行时用例 {@code @Disabled}, 靠 gradlew runClient 验收。
 */
class QatBarTest {

    private static RibbonCommand noopCommand() {
        return new RibbonCommand() {
            @Override public void execute() {}
            @Override public boolean isEnabled() { return true; }
        };
    }

    private static SimpleRibbonToolDefinition toolWithIcon(String path, String name) {
        return new SimpleRibbonToolDefinition(
            ResourceLocation.fromNamespaceAndPath("kp", path),
            Component.literal(name),
            Optional.of(new com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture(0xFFFFFFFF)),
            Optional.empty(),
            Optional.empty(),
            ToolSize.SMALL,
            new ButtonAction(noopCommand(), false)
        );
    }

    private static SimpleRibbonToolDefinition toolWithoutIcon(String path, String name) {
        return new SimpleRibbonToolDefinition(
            ResourceLocation.fromNamespaceAndPath("kp", path),
            Component.literal(name),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            ToolSize.SMALL,
            new ButtonAction(noopCommand(), false)
        );
    }

    /**
     * 验证有 icon 的工具 -> 按钮使用 icon (运行时验收)。
     */
    @Test
    @Disabled("UIElement.<clinit> 需要 NeoForge 运行时; 靠 gradlew runClient 验收")
    void toolWithIconRendersIconButton() {
        var qat = new QatBar();
        var tool = toolWithIcon("tool_a", "Pan");
        qat.rebuild(List.of(tool.getId()), id -> tool);

        // 重建后应至少有 1 个子元素 (实际按钮)
        assertFalse(qat.getChildren().isEmpty(), "QAT 重建后必须有按钮");
        var btn = qat.getChildren().get(0);
        assertInstanceOf(Button.class, btn);
        // icon 已通过 addPreIcon 设置, 此处运行时检查
    }

    /**
     * 验证无 icon 的工具 -> 按钮显示首字母 (运行时验收)。
     */
    @Test
    @Disabled("UIElement.<clinit> 需要 NeoForge 运行时; 靠 gradlew runClient 验收")
    void toolWithoutIconShowsFirstLetter() {
        var qat = new QatBar();
        var tool = toolWithoutIcon("tool_b", "Select");
        qat.rebuild(List.of(tool.getId()), id -> tool);

        var btn = qat.getChildren().get(0);
        assertInstanceOf(Button.class, btn);
        // 文本应为首字母 "S"
        // 实际运行时验证, 此处仅校验类型
    }

    /**
     * 验证空状态显示淡化星标 (运行时验收)。
     */
    @Test
    @Disabled("UIElement.<clinit> 需要 NeoForge 运行时; 靠 gradlew runClient 验收")
    void emptyQatShowsStarHint() {
        var qat = new QatBar();
        qat.rebuild(List.of(), id -> null);
        assertEquals(1, qat.getChildren().size(), "空 QAT 必须有 1 个 hint 元素");
        var hint = qat.getChildren().get(0);
        assertTrue(hint.getClasses().contains("kp-ribbon-qat-empty"),
            "hint 元素必须有 kp-ribbon-qat-empty class");
    }

    /**
     * 验证 rebuild 方法签名 (不触发 clinit)。
     */
    @Test
    void rebuildMethodSignatureMatchesSpec() throws NoSuchMethodException {
        var m = QatBar.class.getMethod("rebuild",
            java.util.List.class,
            java.util.function.Function.class);
        assertEquals(void.class, m.getReturnType(),
            "rebuild 必须返回 void");
    }
}
