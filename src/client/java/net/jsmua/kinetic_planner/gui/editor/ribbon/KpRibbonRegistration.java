package net.jsmua.kinetic_planner.gui.editor.ribbon;

import net.jsmua.kinetic_planner.gui.editor.EditToolState;
import net.jsmua.kinetic_planner.gui.ribbon.api.RibbonToolDefinition;
import net.jsmua.kinetic_planner.gui.ribbon.api.SimpleRibbonTabDefinition;
import net.jsmua.kinetic_planner.gui.ribbon.api.SimpleRibbonToolGroupDefinition;
import net.jsmua.kinetic_planner.gui.ribbon.api.TabDisplayMode;
import net.jsmua.kinetic_planner.gui.ribbon.registry.RibbonRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/**
 * ClientSetup 时注册 KP 自身的 tab/group/component (spec §11.3)。
 *
 * <p>这是 KP 适配层调用 EditToolState.getInstance() 的合法位置 (运行时入口点)。
 * 注册的 RibbonToolDefinition 通过 KpToolDefinitions 创建, 内部用构造注入的 ToolToggleCommand。
 */
public final class KpRibbonRegistration {

    private KpRibbonRegistration() {}

    /** 注册 KP 内置 tab。在 ClientSetup 调用, RibbonRegistry.freeze() 之前。 */
    public static void register(EditToolState toolState) {
        var definitions = new KpToolDefinitions(toolState);
        var tools = definitions.allTools();

        var toolsGroup = new SimpleRibbonToolGroupDefinition(
            ResourceLocation.fromNamespaceAndPath("kp", "group_main"),
            Optional.of(Component.literal("Tools")),
            tools
        );

        var toolsTab = new SimpleRibbonTabDefinition(
            ResourceLocation.fromNamespaceAndPath("kp", "tab_tools"),
            Component.literal("Tools"),
            Optional.empty(),
            100,  // priority: 主 tab 排最左
            List.of(toolsGroup),
            TabDisplayMode.PINNED,
            false,  // 核心 tab, 不允许用户隐藏
            Optional.empty()
        );

        RibbonRegistry.registerTab(toolsTab.getId(), toolsTab);
    }
}
