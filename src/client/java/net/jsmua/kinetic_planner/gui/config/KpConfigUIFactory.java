package net.jsmua.kinetic_planner.gui.config;

import net.jsmua.kinetic_planner.config.KPConfig;
import net.jsmua.kinetic_planner.config.KpClientState;
import net.jsmua.kinetic_planner.config.OverlayControl;
import net.jsmua.kinetic_planner.config.ProviderConfigControl;
import net.jsmua.kinetic_planner.gui.widgets.ConfigStepperRow;
import net.jsmua.kinetic_planner.gui.widgets.ConfigToggleRow;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Tab;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TabView;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import dev.vfyjxf.taffy.style.TaffyDisplay;
import net.jsmua.kinetic_planner.data.ProviderConfig;
import net.jsmua.kinetic_planner.gui.theme.KpTheme;
import net.jsmua.kinetic_planner.mapadapter.MapOverlayDispatcher;
import net.minecraft.network.chat.Component;

/**
 * KP 配置面板 UI 工厂 - 构建 LDLib2 {@code UIElement} 树、注册 LSS 样式表、
 * 绑定 {@link ProviderConfig} 数据到控件回调。
 *
 * <p>spec §9.1：负责 UI 树构建、控件数据绑定、Tab 切换、LSS 主题注册。
 * 行构建器（toggle/stepper）已提取到 {@link ConfigToggleRow} / {@link ConfigStepperRow}。
 */
public final class KpConfigUIFactory {

    private static final int PANEL_WIDTH = 200;
    private static final int PANEL_RIGHT_MARGIN = 4;
    private static final int PANEL_TOP = 24;

    private KpConfigUIFactory() {}

    /**
     * 构建 KP 配置面板 {@link ModularUI}。
     */
    public static ModularUI create() {
        UIElement root = buildPanelRoot();
        var ui = UI.of(root);
        return ModularUI.of(ui);
    }

    private static UIElement buildPanelRoot() {
        var root = new UIElement();
        root.addClass("kp-panel");
        root.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.right(PANEL_RIGHT_MARGIN);
            layout.top(PANEL_TOP);
            layout.width(PANEL_WIDTH);
            layout.heightAuto();
            layout.display(TaffyDisplay.FLEX);
            layout.flexDirection(FlexDirection.COLUMN);
        });

        root.addChild(buildTitleBar());
        root.addChild(buildProviderTabView());
        root.addChild(buildHintLabel());
        return root;
    }

    private static UIElement buildTitleBar() {
        var titleBar = new UIElement();
        titleBar.addClass("kp-title-row");

        var title = new Label();
        title.setValue(Component.literal("Kinetic Planner"));
        title.addClass("kp-title");
        title.layout(layout -> {
            layout.flex(1);
            layout.height(9);
        });

        var closeButton = new Button();
        closeButton.setText("×");
        closeButton.setOnClick(event -> KpClientState.setConfigPanelVisible(false));
        closeButton.layout(layout -> {
            layout.width(18);
            layout.height(14);
        });

        titleBar.addChild(title);
        titleBar.addChild(closeButton);
        return titleBar;
    }

    private static TabView buildProviderTabView() {
        var tabView = new TabView();
        tabView.layout(layout -> {
            layout.widthPercent(100);
            layout.flexDirection(FlexDirection.COLUMN_REVERSE);
        });

        tabView.tabHeaderContainer(container -> container.style(style ->
            style.backgroundTexture(new ColorRectTexture(KpTheme.PANEL_BG_TRANS))
        ).layout(layout -> {
            layout.paddingHorizontal(3);
            layout.paddingVertical(2);
        }));

        tabView.tabContentContainer(container -> container.style(style ->
            style.backgroundTexture(new ColorRectTexture(KpTheme.PANEL_BG_TRANS))
        ).layout(layout -> {
            layout.paddingAll(4);
            layout.flexGrow(1);
        }));

        for (String modId : MapOverlayDispatcher.getRegisteredModIds()) {
            var tab = new Tab();
            tab.setText(modId);
            tab.addClass("kp-tab");
            if (MapOverlayDispatcher.isCircuitBroken(modId)) {
                tab.addClass("kp-tab-fused");
            }
            tab.layout(layout -> {
                layout.height(18);
                layout.paddingHorizontal(6);
                layout.paddingVertical(2);
            });

            UIElement content = buildProviderConfigRows(modId);
            tabView.addTab(tab, content);
        }
        return tabView;
    }

    /**
     * 构建指定 provider 的配置项列表。
     *
     * <p>行构建委托 {@link ConfigToggleRow} / {@link ConfigStepperRow}，
     * 控件回调直接调用 {@link ProviderConfigControl} 或 {@link OverlayControl}，即时生效。
     */
    private static UIElement buildProviderConfigRows(String modId) {
        var column = new UIElement();
        column.layout(layout -> {
            layout.flexDirection(FlexDirection.COLUMN);
            layout.widthPercent(100);
            layout.gapAll(2);
        });

        ProviderConfig config = KPConfig.getInstance().getProviderConfig(modId);
        if (config == null) {
            var error = new Label();
            error.setValue(Component.literal("Unknown provider: " + modId));
            error.addClass("kp-text-secondary");
            column.addChild(error);
            return column;
        }

        boolean fused = MapOverlayDispatcher.isCircuitBroken(modId);

        column.addChild(ConfigToggleRow.create("Enabled", config.enabled(), fused, value -> {
            if (value) ProviderConfigControl.enable(modId);
            else ProviderConfigControl.disable(modId);
        }));

        column.addChild(ConfigStepperRow.createInt("Priority", config.priority(),
            1, 0, 100, fused, next -> {
                ProviderConfigControl.setParam(modId, "priority", String.valueOf(next));
            }));

        column.addChild(ConfigStepperRow.createFloat("Line Width", config.lineWidthScale(),
            0.05f, 0.1f, 10.0f, fused, next -> {
                ProviderConfigControl.setParam(modId, "lineWidthScale", String.valueOf(next));
            }));

        column.addChild(ConfigStepperRow.createFloat("Alpha", config.alphaScale(),
            0.05f, 0.0f, 1.0f, fused, next -> {
                ProviderConfigControl.setParam(modId, "alphaScale", String.valueOf(next));
            }));

        column.addChild(ConfigToggleRow.create("Dashed", config.dashed(), fused, value -> {
            ProviderConfigControl.setParam(modId, "dashed", String.valueOf(value));
        }));

        boolean showCreate = OverlayControl.isShowCreateTrackMap();
        column.addChild(ConfigToggleRow.create("Show Create Track Map", showCreate, false, value -> {
            OverlayControl.setShowCreateTrackMap(value);
        }));

        return column;
    }

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
