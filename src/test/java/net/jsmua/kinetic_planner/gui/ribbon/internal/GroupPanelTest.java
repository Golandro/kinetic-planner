package net.jsmua.kinetic_planner.gui.ribbon.internal;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import net.jsmua.kinetic_planner.gui.ribbon.api.ButtonAction;
import net.jsmua.kinetic_planner.gui.ribbon.api.RibbonCommand;
import net.jsmua.kinetic_planner.gui.ribbon.api.SimpleRibbonToolDefinition;
import net.jsmua.kinetic_planner.gui.ribbon.api.SimpleRibbonToolGroupDefinition;
import net.jsmua.kinetic_planner.gui.ribbon.api.ToolSize;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link GroupPanel#build} 行为测试。
 *
 * <p><b>测试环境限制:</b> 同 {@link ToolWidgetFactoryTest}, 直接构造触发 UIElement.<clinit>
 * → LDLib2 clinit 陷阱。运行时验收用例 {@code @Disabled}。
 */
class GroupPanelTest {

    private static RibbonCommand noopCommand() {
        return new RibbonCommand() {
            @Override public void execute() {}
            @Override public boolean isEnabled() { return true; }
        };
    }

    private static SimpleRibbonToolDefinition buttonTool(String path) {
        return new SimpleRibbonToolDefinition(
            ResourceLocation.fromNamespaceAndPath("kp", path),
            Component.literal(path),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            ToolSize.SMALL,
            new ButtonAction(noopCommand(), false)
        );
    }

    /**
     * 验证返回 GroupPanel: COLUMN 外壳 + 工具行 + 底部 Label (运行时验收)。
     */
    @Test
    @Disabled("UIElement.<clinit> 需要 NeoForge 运行时; 靠 gradlew runClient 验收")
    void buildReturnsColumnShellWithToolRowAndLabel() {
        var group = new SimpleRibbonToolGroupDefinition(
            ResourceLocation.fromNamespaceAndPath("kp", "g1"),
            Optional.of(Component.literal("Group 1")),
            List.of(buttonTool("a"), buttonTool("b"))
        );
        var panel = GroupPanel.build(group, null);
        assertNotNull(panel);
        assertTrue(panel.getClasses().contains("kp-ribbon-group"));
        // GroupPanel 应包含: 工具行 (UIElement.kp-ribbon-tool-row) + 底部 Label
        // 注意: 子元素顺序由实现决定, 此处仅校验数量与类型存在
        var children = panel.getChildren();
        assertFalse(children.isEmpty(), "GroupPanel 必须至少包含工具行");
        // 第一个子元素是工具行 UIElement
        var toolRow = children.get(0);
        assertTrue(toolRow.getClasses().contains("kp-ribbon-tool-row"),
            "第一个子元素必须是工具行 (kp-ribbon-tool-row)");
        // 工具行子元素数量 = group.getTools().size()
        assertEquals(group.getTools().size(), toolRow.getChildren().size(),
            "工具行内子元素数量必须等于 tools 数量");
    }

    /**
     * 验证无 displayName 时 GroupPanel 不包含 Label (运行时验收)。
     */
    @Test
    @Disabled("UIElement.<clinit> 需要 NeoForge 运行时; 靠 gradlew runClient 验收")
    void buildWithoutDisplayNameHasNoLabel() {
        var group = new SimpleRibbonToolGroupDefinition(
            ResourceLocation.fromNamespaceAndPath("kp", "g2"),
            Optional.empty(),
            List.of(buttonTool("a"))
        );
        var panel = GroupPanel.build(group, null);
        // 只有一个子元素 (工具行), 无 Label
        assertEquals(1, panel.getChildren().size());
    }

    /**
     * 验证 build 方法签名接受 nullable RibbonToggleGroup (不触发 clinit)。
     */
    @Test
    void buildMethodAcceptsNullableToggleGroup() throws NoSuchMethodException {
        var m = GroupPanel.class.getMethod("build",
            net.jsmua.kinetic_planner.gui.ribbon.api.RibbonToolGroupDefinition.class,
            net.jsmua.kinetic_planner.gui.ribbon.internal.toggle.RibbonToggleGroup.class);
        assertEquals(UIElement.class, m.getReturnType(),
            "build 必须返回 UIElement");
    }
}
