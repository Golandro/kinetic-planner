package net.jsmua.kinetic_planner.config;

import com.lowdragmc.lowdraglib2.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Tab;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TabView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Toggle;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventListener;
import com.lowdragmc.lowdraglib2.gui.ui.style.LayoutStyle;
import com.lowdragmc.lowdraglib2.gui.ui.style.BasicStyle;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.jsmua.kinetic_planner.data.ProviderConfig;
import net.jsmua.kinetic_planner.mapadapter.MapOverlayDispatcher;
import net.jsmua.kinetic_planner.mapadapter.MapOverlayProvider;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * KP 配置面板 UI 工厂 - 构建 LDLib2 {@code UIElement} 树、注册 LSS 样式表、
 * 绑定 {@link ProviderConfig} 数据到控件回调。
 *
 * <p>spec §9.1：负责 UI 树构建、控件数据绑定、Tab 切换、LSS 主题注册。
 *
 * <p><b>UI 树结构</b>（spec §4.3）：
 * <pre>
 * UIElement (root, position=absolute, right=4, top=24, w=200, h=auto)
 * ├── kp-panel class
 * ├── TabView (provider tabs + per-tab content)
 * │   └── Tab content: provider 配置项列表 column
 * └── Label (提示文本, textSecondary)
 * </pre>
 *
 * <p><b>Tab 切换策略</b>（基于 API 验证 {@code 2026-07-27-ldlib2-api-verification.md} §1.4 + §6）：
 * 每个 Tab 自带 content panel，{@code addTab} 内部已自动管理显示/隐藏。
 * 故为每个 provider 构建独立的 content panel（含该 provider 的配置行），
 * <b>不</b>在 {@code setOnTabSelected} 回调中重建子树。
 */
public final class KpConfigUIFactory {

    /** 面板宽度（spec §4.1：200px 固定） */
    private static final int PANEL_WIDTH = 200;
    /** 面板距右边缘 4px（spec §4.1） */
    private static final int PANEL_RIGHT_MARGIN = 4;
    /** 面板距顶部 24px（spec §4.1：齿轮按钮下方） */
    private static final int PANEL_TOP = 24;

    private KpConfigUIFactory() {}

    /**
     * 步进器数值计算 + clamp 纯函数（float 重载）。
     *
     * <p>spec §5.2：步进器在 UI 层 clamp 后再传给 {@link ProviderConfigControl}，
     * 保证显示值与配置值一致。
     */
    public static float computeSteppedValue(float current, float step, boolean increment,
                                            float min, float max) {
        float next = increment ? current + step : current - step;
        return Math.max(min, Math.min(max, next));
    }

    /**
     * 步进器数值计算 + clamp 纯函数（int 重载，用于 priority）。
     */
    public static int computeSteppedValue(int current, int step, boolean increment,
                                         int min, int max) {
        int next = increment ? current + step : current - step;
        return Math.max(min, Math.min(max, next));
    }

    /**
     * 构建 KP 配置面板 {@link ModularUI}。
     *
     * <p>spec §9.1：构建 UI 树 + 注册 {@link KpStylesheet} + 绑定 provider 数据。
     *
     * @return 初始化完成的 {@link ModularUI}，由调用方 {@code init(width, height)} 后渲染
     */
    public static ModularUI create() {
        UIElement root = buildPanelRoot();
        var stylesheet = KpStylesheet.create();
        var ui = UI.of(root, stylesheet);
        return ModularUI.of(ui);
    }

    /**
     * 构建面板根容器。
     *
     * <p>定位：右上角，距右 4px，距顶 24px。宽度 200px，高度自适应内容。
     * 使用 {@code position: ABSOLUTE} + {@code right/top} 让布局引擎自动处理屏幕尺寸变化。
     */
    private static UIElement buildPanelRoot() {
        var root = new UIElement();
        root.addClass("kp-panel");
        root.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.right(PANEL_RIGHT_MARGIN);
            layout.top(PANEL_TOP);
            layout.width(PANEL_WIDTH);
            layout.heightAuto();
            layout.display(dev.vfyjxf.taffy.style.TaffyDisplay.FLEX);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.paddingAll(8);
            layout.gapAll(4);
        });
        root.style(style -> {
            // SDF 圆角 + 1px 黑边（LSS 已在 .kp-panel 类中定义，这里作为内联兜底）
        });

        // 标题栏：gear icon + 标题 + 关闭按钮
        root.addChild(buildTitleBar());

        // TabView：provider tabs + 每 tab 的配置项列表
        root.addChild(buildProviderTabView());

        // 提示文本
        root.addChild(buildHintLabel());

        return root;
    }

    /**
     * 构建标题栏：左为 "Kinetic Planner" 标题，右为关闭按钮 [×]。
     */
    private static UIElement buildTitleBar() {
        var titleBar = new UIElement();
        titleBar.addClass("kp-title-row");
        titleBar.layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(dev.vfyjxf.taffy.style.AlignItems.CENTER);
            layout.justifyContent(dev.vfyjxf.taffy.style.AlignContent.SPACE_BETWEEN);
            layout.widthPercent(100);
        });

        // 标题
        var title = new Label();
        title.setValue(Component.literal("Kinetic Planner"));
        title.addClass("kp-title");

        // 关闭按钮
        var closeButton = new Button();
        closeButton.setText("×");
        closeButton.setOnClick(event -> KpClientState.setConfigPanelVisible(false));

        titleBar.addChild(title);
        titleBar.addChild(closeButton);
        return titleBar;
    }

    /**
     * 构建 provider TabView。
     *
     * <p>每个 provider 一个 Tab，Tab 的 content 是该 provider 的配置项列表。
     * 首个 Tab 自动选中（spec §8.4 + API 验证 §1.4）。
     */
    private static TabView buildProviderTabView() {
        var tabView = new TabView();
        tabView.layout(layout -> {
            layout.widthPercent(100);
            layout.flexDirection(FlexDirection.ROW);
        });

        List<MapOverlayProvider> providers = MapOverlayDispatcher.registeredProviders();
        for (MapOverlayProvider provider : providers) {
            var tab = new Tab();
            tab.setText(provider.modId());
            // 熔断 provider 标记 danger 色
            if (MapOverlayDispatcher.isCircuitBroken(provider.modId())) {
                tab.addClass("kp-tab-fused");
            }
            // 选中态：底部 2px KP 紫（通过 ColorBorderTexture inset 1px 底边模拟）
            // 注意：LDLib2 有 typo（见 API 验证 §1.4.3），用 __selected__ 类做样式
            tab.getTabStyle().selectedTexture(
                new ColorBorderTexture(-1, KpStylesheet.KP_ACCENT));

            // 构建该 provider 的配置项列表作为 Tab content
            UIElement content = buildProviderConfigRows(provider.modId());
            tabView.addTab(tab, content);
        }
        return tabView;
    }

    /**
     * 构建指定 provider 的配置项列表。
     *
     * <p>spec §9.1：每行 label + 控件，控件回调直接调用 {@link ProviderConfigControl}
     * 或 {@link OverlayControl}，即时生效（spec §5.1）。
     *
     * @param modId provider mod ID
     * @return 配置项列表 column 容器
     */
    private static UIElement buildProviderConfigRows(String modId) {
        var column = new UIElement();
        column.layout(layout -> {
            layout.flexDirection(FlexDirection.COLUMN);
            layout.widthPercent(100);
            layout.gapAll(2);
        });

        ProviderConfig config = KPConfig.getProviderConfig(modId);
        if (config == null) {
            // 未知 provider：显示错误提示
            var error = new Label();
            error.setValue(Component.literal("Unknown provider: " + modId));
            error.addClass("kp-text-secondary");
            column.addChild(error);
            return column;
        }

        boolean fused = MapOverlayDispatcher.isCircuitBroken(modId);

        // Enabled toggle
        column.addChild(buildToggleRow("Enabled", config.enabled(), fused, value -> {
            if (value) ProviderConfigControl.enable(modId);
            else ProviderConfigControl.disable(modId);
        }));

        // Priority stepper (int, 0-100, step 1)
        column.addChild(buildStepperRowInt("Priority", config.priority(),
            1, 0, 100, fused, next -> {
                ProviderConfigControl.setParam(modId, "priority", String.valueOf(next));
            }));

        // Line Width stepper (float, 0.1-10.0, step 0.05)
        column.addChild(buildStepperRowFloat("Line Width", config.lineWidthScale(),
            0.05f, 0.1f, 10.0f, fused, next -> {
                ProviderConfigControl.setParam(modId, "lineWidthScale", String.valueOf(next));
            }));

        // Alpha stepper (float, 0.0-1.0, step 0.05)
        column.addChild(buildStepperRowFloat("Alpha", config.alphaScale(),
            0.05f, 0.0f, 1.0f, fused, next -> {
                ProviderConfigControl.setParam(modId, "alphaScale", String.valueOf(next));
            }));

        // Dashed toggle
        column.addChild(buildToggleRow("Dashed", config.dashed(), fused, value -> {
            ProviderConfigControl.setParam(modId, "dashed", String.valueOf(value));
        }));

        // Show Create Track Map toggle (全局，非 per-provider)
        boolean showCreate = OverlayControl.isShowCreateTrackMap();
        column.addChild(buildToggleRow("Show Create Track Map", showCreate, false, value -> {
            OverlayControl.setShowCreateTrackMap(value);
        }));

        return column;
    }

    /**
     * 构建 toggle 配置行：label 左 + Toggle 右。
     *
     * @param label    行标题
     * @param initial  初始状态
     * @param disabled 是否禁用（熔断 provider）
     * @param callback 状态变化回调（参数为 new value）
     */
    private static UIElement buildToggleRow(String label, boolean initial, boolean disabled,
                                             java.util.function.Consumer<Boolean> callback) {
        var row = new UIElement();
        row.addClass("kp-config-row");
        row.layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(dev.vfyjxf.taffy.style.AlignItems.CENTER);
            layout.justifyContent(dev.vfyjxf.taffy.style.AlignContent.SPACE_BETWEEN);
            layout.widthPercent(100);
        });

        var labelEl = new Label();
        labelEl.setValue(Component.literal(label));

        var toggle = new Toggle();
        toggle.setOn(initial);
        toggle.setOnToggleChanged(callback::accept);
        if (disabled) {
            toggle.setActive(false);
        }

        row.addChild(labelEl);
        row.addChild(toggle);
        return row;
    }

    /**
     * 构建 int 步进器行：label + [−] value [+]。
     */
    private static UIElement buildStepperRowInt(String label, int initial, int step,
                                                int min, int max, boolean disabled,
                                                java.util.function.Consumer<Integer> callback) {
        var row = new UIElement();
        row.addClass("kp-config-row");
        row.layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(dev.vfyjxf.taffy.style.AlignItems.CENTER);
            layout.justifyContent(dev.vfyjxf.taffy.style.AlignContent.SPACE_BETWEEN);
            layout.widthPercent(100);
        });

        var labelEl = new Label();
        labelEl.setValue(Component.literal(label));

        // 步进器容器：[−] value [+]
        var stepper = new UIElement();
        stepper.layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(dev.vfyjxf.taffy.style.AlignItems.CENTER);
            layout.gapAll(2);
        });

        // 用 holder 持有当前值（final 引用，lambda 修改内部值）
        var holder = new int[]{initial};
        var valueLabel = new Label();
        valueLabel.setValue(Component.literal(String.valueOf(initial)));

        Button minusBtn = new Button();
        minusBtn.setText("−");
        minusBtn.setOnClick(event -> {
            int next = computeSteppedValue(holder[0], step, false, min, max);
            holder[0] = next;
            valueLabel.setValue(Component.literal(String.valueOf(next)));
            callback.accept(next);
        });

        Button plusBtn = new Button();
        plusBtn.setText("+");
        plusBtn.setOnClick(event -> {
            int next = computeSteppedValue(holder[0], step, true, min, max);
            holder[0] = next;
            valueLabel.setValue(Component.literal(String.valueOf(next)));
            callback.accept(next);
        });

        if (disabled) {
            minusBtn.setActive(false);
            plusBtn.setActive(false);
        }

        stepper.addChild(minusBtn);
        stepper.addChild(valueLabel);
        stepper.addChild(plusBtn);

        row.addChild(labelEl);
        row.addChild(stepper);
        return row;
    }

    /**
     * 构建 float 步进器行：label + [−] value [+]。
     *
     * <p>显示格式：{@code %.2f}（两位小数）。
     */
    private static UIElement buildStepperRowFloat(String label, float initial, float step,
                                                  float min, float max, boolean disabled,
                                                  java.util.function.Consumer<Float> callback) {
        var row = new UIElement();
        row.addClass("kp-config-row");
        row.layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(dev.vfyjxf.taffy.style.AlignItems.CENTER);
            layout.justifyContent(dev.vfyjxf.taffy.style.AlignContent.SPACE_BETWEEN);
            layout.widthPercent(100);
        });

        var labelEl = new Label();
        labelEl.setValue(Component.literal(label));

        var stepper = new UIElement();
        stepper.layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(dev.vfyjxf.taffy.style.AlignItems.CENTER);
            layout.gapAll(2);
        });

        var holder = new float[]{initial};
        var valueLabel = new Label();
        valueLabel.setValue(Component.literal(String.format("%.2f", initial)));

        Button minusBtn = new Button();
        minusBtn.setText("−");
        minusBtn.setOnClick(event -> {
            float next = computeSteppedValue(holder[0], step, false, min, max);
            holder[0] = next;
            valueLabel.setValue(Component.literal(String.format("%.2f", next)));
            callback.accept(next);
        });

        Button plusBtn = new Button();
        plusBtn.setText("+");
        plusBtn.setOnClick(event -> {
            float next = computeSteppedValue(holder[0], step, true, min, max);
            holder[0] = next;
            valueLabel.setValue(Component.literal(String.format("%.2f", next)));
            callback.accept(next);
        });

        if (disabled) {
            minusBtn.setActive(false);
            plusBtn.setActive(false);
        }

        stepper.addChild(minusBtn);
        stepper.addChild(valueLabel);
        stepper.addChild(plusBtn);

        row.addChild(labelEl);
        row.addChild(stepper);
        return row;
    }

    /**
     * 构建底部提示文本（次级色）。
     */
    private static UIElement buildHintLabel() {
        var hint = new Label();
        hint.setValue(Component.literal("Changes apply instantly"));
        hint.addClass("kp-text-secondary");
        hint.layout(layout -> {
            layout.widthPercent(100);
            layout.paddingTop(4);
        });
        return hint;
    }
}
