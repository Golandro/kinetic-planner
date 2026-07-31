package net.jsmua.kinetic_planner.gui.editor.ribbon;

import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import net.jsmua.kinetic_planner.config.IKPConfig;
import net.jsmua.kinetic_planner.gui.editor.EditToolState;
import net.jsmua.kinetic_planner.gui.editor.KpEditorScreen;
import net.jsmua.kinetic_planner.gui.editor.KpMapEditor;
import net.jsmua.kinetic_planner.gui.ribbon.api.ButtonAction;
import net.jsmua.kinetic_planner.gui.ribbon.api.RibbonCommand;
import net.jsmua.kinetic_planner.gui.ribbon.api.RibbonHeaderComponent;
import net.jsmua.kinetic_planner.gui.ribbon.api.RibbonToggleCommand;
import net.jsmua.kinetic_planner.gui.ribbon.api.RibbonToolDefinition;
import net.jsmua.kinetic_planner.gui.ribbon.api.SeparatorAction;
import net.jsmua.kinetic_planner.gui.ribbon.api.SimpleContextualTabGroup;
import net.jsmua.kinetic_planner.gui.ribbon.api.SimpleRibbonHeaderComponent;
import net.jsmua.kinetic_planner.gui.ribbon.api.SimpleRibbonTabDefinition;
import net.jsmua.kinetic_planner.gui.ribbon.api.SimpleRibbonToolDefinition;
import net.jsmua.kinetic_planner.gui.ribbon.api.SimpleRibbonToolGroupDefinition;
import net.jsmua.kinetic_planner.gui.ribbon.api.TabDisplayMode;
import net.jsmua.kinetic_planner.gui.ribbon.api.TextTooltip;
import net.jsmua.kinetic_planner.gui.ribbon.api.ToolSize;
import net.jsmua.kinetic_planner.gui.ribbon.registry.RibbonRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/**
 * ClientSetup 时注册 KP 自身的 tab/group/header/component (spec §11.3)。
 *
 * <p>这是 KP 适配层调用 EditToolState.getInstance() 的合法位置 (运行时入口点)。
 * 注册的 RibbonToolDefinition 通过 KpToolDefinitions 创建, 内部用构造注入的命令。
 *
 * <p>Demo 内容 (展示框架能力, 非生产工具):
 * <ul>
 *   <li>Tools tab (priority 100, PINNED): 工具组 (toggle, LARGE) + 选择组 (一次性按钮 + 分隔符, SMALL)</li>
 *   <li>View tab (priority 200, PINNED): 图层/标签/线宽开关 (config 布尔 toggle, SMALL) + 上下文选项卡组切换按钮</li>
 *   <li>Contextual demo group (priority 300, CONTEXTUAL): 上下文激活时显示的 "Ctx Tools" tab (LARGE 一次性按钮)</li>
 *   <li>Header: LEADING "KP" 标志 + TRAILING Settings/Help 按钮</li>
 * </ul>
 */
public final class KpRibbonRegistration {

    private KpRibbonRegistration() {}

    /** 注册 KP 内置 tab/group/header。在 ClientSetup 调用, RibbonRegistry.freeze() 之前。 */
    public static void register(EditToolState toolState, IKPConfig config) {
        registerToolsTab(toolState);
        registerViewTab(config);
        registerDemoContextualGroup();
        registerHeaderComponents();
    }

    // ===== Tools tab (priority 100, PINNED) =====

    private static void registerToolsTab(EditToolState toolState) {
        var definitions = new KpToolDefinitions(toolState);

        var toolsGroup = new SimpleRibbonToolGroupDefinition(
            rl("kp", "group_tools"),
            Optional.of(Component.literal("Tools")),
            definitions.allTools(),
            Optional.of(KpToolDefinitions.TOOLS_MUTEX_ID)
        );

        var selectionGroup = new SimpleRibbonToolGroupDefinition(
            rl("kp", "group_selection"),
            Optional.of(Component.literal("Selection")),
            List.of(
                smallButton("tool_clear", "Clear",
                    new RunnableCommand(toolState::clearSelection)),
                separator("sep_selection"),
                smallButton("tool_invert", "Invert",
                    new RunnableCommand(() -> {}))  // demo stub
            )
        );

        var toolsTab = new SimpleRibbonTabDefinition(
            rl("kp", "tab_tools"),
            Component.literal("Tools"),
            Optional.empty(),
            100,
            List.of(toolsGroup, selectionGroup),
            TabDisplayMode.PINNED,
            false,  // 核心 tab, 不允许用户隐藏
            Optional.empty()
        );
        RibbonRegistry.registerTab(toolsTab.getId(), toolsTab);
    }

    // ===== View tab (priority 200, PINNED) =====

    private static void registerViewTab(IKPConfig config) {
        var layersGroup = new SimpleRibbonToolGroupDefinition(
            rl("kp", "group_layers"),
            Optional.of(Component.literal("Layers")),
            List.of(
                smallToggle("tool_tracks", "Tracks",
                    new BooleanToggleCommand(config::isLayerTracksVisible, config::setLayerTracksVisible)),
                smallToggle("tool_nodes", "Nodes",
                    new BooleanToggleCommand(config::isLayerNodesVisible, config::setLayerNodesVisible)),
                smallToggle("tool_edgepts", "Edge Pts",
                    new BooleanToggleCommand(config::isLayerEdgePointsVisible, config::setLayerEdgePointsVisible))
            )
        );

        var labelsGroup = new SimpleRibbonToolGroupDefinition(
            rl("kp", "group_labels"),
            Optional.of(Component.literal("Labels")),
            List.of(
                smallToggle("tool_nodelabels", "Node Labels",
                    new BooleanToggleCommand(config::isShowNodeLabels, config::setShowNodeLabels)),
                smallToggle("tool_stationnames", "Station Names",
                    new BooleanToggleCommand(config::isShowStationNames, config::setShowStationNames))
            )
        );

        var linesGroup = new SimpleRibbonToolGroupDefinition(
            rl("kp", "group_lines"),
            Optional.of(Component.literal("Lines")),
            List.of(
                smallToggle("tool_constwidth", "Const Width",
                    new BooleanToggleCommand(config::isConstantScreenLineWidth, config::setConstantScreenLineWidth))
            )
        );

        var contextGroup = new SimpleRibbonToolGroupDefinition(
            rl("kp", "group_context"),
            Optional.of(Component.literal("Context")),
            List.of(
                smallToggle("tool_toggle_ctx", "Ctx Tab",
                    new BooleanToggleCommand(
                        () -> currentEditor().map(KpMapEditor::isDemoContextActive).orElse(false),
                        active -> currentEditor().ifPresent(e -> e.setDemoContextActive(active))))
            )
        );

        var viewTab = new SimpleRibbonTabDefinition(
            rl("kp", "tab_view"),
            Component.literal("View"),
            Optional.empty(),
            200,
            List.of(layersGroup, labelsGroup, linesGroup, contextGroup),
            TabDisplayMode.PINNED,
            true,
            Optional.empty()
        );
        RibbonRegistry.registerTab(viewTab.getId(), viewTab);
    }

    // ===== Contextual demo group (priority 300, CONTEXTUAL) =====

    private static void registerDemoContextualGroup() {
        var demoGroup = new SimpleRibbonToolGroupDefinition(
            rl("kp", "group_demo"),
            Optional.of(Component.literal("Demo")),
            List.of(
                largeButton("tool_action_a", "Action A", new RunnableCommand(() -> {})),
                largeButton("tool_action_b", "Action B", new RunnableCommand(() -> {}))
            )
        );

        var ctxTab = new SimpleRibbonTabDefinition(
            rl("kp", "tab_ctx_demo"),
            Component.literal("Ctx Tools"),
            Optional.empty(),
            300,
            List.of(demoGroup),
            TabDisplayMode.CONTEXTUAL,
            false,
            Optional.of(rl("kp", "demo_context_group"))
        );

        var ctxGroup = new SimpleContextualTabGroup(
            rl("kp", "demo_context_group"),
            Component.literal("Demo Context"),
            Optional.empty(),
            300,
            Optional.of(0x8B5CF6),  // purple accent (RGB)
            List.of(ctxTab),
            TabDisplayMode.PINNED
        );
        RibbonRegistry.registerContextualGroup(ctxGroup.getId(), ctxGroup);
    }

    // ===== Header components =====

    private static void registerHeaderComponents() {
        var logo = new SimpleRibbonHeaderComponent(
            rl("kp", "header_logo"),
            RibbonHeaderComponent.Placement.LEADING,
            200,
            () -> {
                var btn = new Button();
                btn.setText(Component.literal("KP"));
                return btn;
            }
        );

        var settings = new SimpleRibbonHeaderComponent(
            rl("kp", "header_settings"),
            RibbonHeaderComponent.Placement.TRAILING,
            100,
            () -> {
                var btn = new Button();
                btn.setText(Component.literal("Settings"));
                return btn;
            }
        );

        var help = new SimpleRibbonHeaderComponent(
            rl("kp", "header_help"),
            RibbonHeaderComponent.Placement.TRAILING,
            200,
            () -> {
                var btn = new Button();
                btn.setText(Component.literal("?"));
                return btn;
            }
        );

        RibbonRegistry.registerHeaderComponent(logo.getId(), logo);
        RibbonRegistry.registerHeaderComponent(settings.getId(), settings);
        RibbonRegistry.registerHeaderComponent(help.getId(), help);
    }

    // ===== definition helpers =====

    /**
     * 查找当前活跃的 {@link KpMapEditor} 实例 (通过当前 Screen 是否为 {@link KpEditorScreen} 判断)。
     *
     * <p>用于 View tab 的 "Ctx Tab" 演示开关: 注册时 ({@code ClientSetup}) 编辑器尚未创建,
     * 命令的 getter/setter 在运行时被调用, 此时通过此方法查找当前编辑器实例。
     *
     * @return 当前活跃的 KpMapEditor; 无编辑器打开时返回 {@link Optional#empty()}
     */
    private static Optional<KpMapEditor> currentEditor() {
        var screen = Minecraft.getInstance().screen;
        return screen instanceof KpEditorScreen kp ? Optional.of(kp.getEditor()) : Optional.empty();
    }

    private static ResourceLocation rl(String ns, String path) {
        return ResourceLocation.fromNamespaceAndPath(ns, path);
    }

    private static RibbonToolDefinition smallButton(String path, String name, RibbonCommand command) {
        return new SimpleRibbonToolDefinition(
            rl("kp", path),
            Component.literal(name),
            Optional.empty(),
            Optional.of(new TextTooltip(Component.literal(name))),
            Optional.empty(),
            ToolSize.SMALL,
            new ButtonAction(command, false)
        );
    }

    private static RibbonToolDefinition smallToggle(String path, String name, RibbonToggleCommand command) {
        return new SimpleRibbonToolDefinition(
            rl("kp", path),
            Component.literal(name),
            Optional.empty(),
            Optional.of(new TextTooltip(Component.literal(name))),
            Optional.empty(),
            ToolSize.SMALL,
            new ButtonAction(command, true)
        );
    }

    private static RibbonToolDefinition largeButton(String path, String name, RibbonCommand command) {
        return new SimpleRibbonToolDefinition(
            rl("kp", path),
            Component.literal(name),
            Optional.empty(),
            Optional.of(new TextTooltip(Component.literal(name))),
            Optional.empty(),
            ToolSize.LARGE,
            new ButtonAction(command, false)
        );
    }

    private static RibbonToolDefinition separator(String path) {
        return new SimpleRibbonToolDefinition(
            rl("kp", path),
            Component.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            ToolSize.SMALL,
            new SeparatorAction()
        );
    }
}
