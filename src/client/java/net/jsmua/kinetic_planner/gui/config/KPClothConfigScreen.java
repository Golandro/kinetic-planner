package net.jsmua.kinetic_planner.gui.config;

import net.jsmua.kinetic_planner.config.IKPConfig;
import net.jsmua.kinetic_planner.config.KPConfig;
import net.jsmua.kinetic_planner.KineticPlannerMod;
import net.jsmua.kinetic_planner.instrument.WorldTreeReadOverlay;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Cloth Config 配置屏幕构建器。
 */
public final class KPClothConfigScreen {

    private KPClothConfigScreen() {}

    public static Screen build(Screen parent) {
        IKPConfig config = KPConfig.getInstance();
        ConfigBuilder builder = ConfigBuilder.create()
            .setParentScreen(parent)
            .setTitle(Component.literal("Kinetic Planner Config"));

        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        // [overlay]
        ConfigCategory overlay = builder.getOrCreateCategory(Component.literal("Overlay"));
        overlay.addEntry(entryBuilder.startBooleanToggle(
                Component.literal("Enabled"), config.isOverlayEnabled())
            .setDefaultValue(true)
            .setSaveConsumer(config::setOverlayEnabled)
            .build());

        // [layers]
        ConfigCategory layers = builder.getOrCreateCategory(Component.literal("Layers"));
        layers.addEntry(entryBuilder.startBooleanToggle(
                Component.literal("Tracks"), config.isLayerTracksVisible())
            .setDefaultValue(true)
            .setSaveConsumer(config::setLayerTracksVisible)
            .build());
        layers.addEntry(entryBuilder.startBooleanToggle(
                Component.literal("Nodes"), config.isLayerNodesVisible())
            .setDefaultValue(true)
            .setSaveConsumer(config::setLayerNodesVisible)
            .build());
        layers.addEntry(entryBuilder.startBooleanToggle(
                Component.literal("Edge Points"), config.isLayerEdgePointsVisible())
            .setDefaultValue(true)
            .setSaveConsumer(config::setLayerEdgePointsVisible)
            .build());

        // [theme]
        ConfigCategory theme = builder.getOrCreateCategory(Component.literal("Theme"));
        theme.addEntry(entryBuilder.startStrField(
                Component.literal("Active Theme"), config.getActiveThemeName())
            .setDefaultValue("default")
            .setSaveConsumer(config::setActiveThemeName)
            .build());
        theme.addEntry(entryBuilder.startBooleanToggle(
                Component.literal("Constant Screen Line Width"),
                config.isConstantScreenLineWidth())
            .setDefaultValue(true)
            .setSaveConsumer(config::setConstantScreenLineWidth)
            .build());
        theme.addEntry(entryBuilder.startDoubleField(
                Component.literal("Fixed Line Width (px)"),
                config.getFixedScreenLineWidthPx())
            .setDefaultValue(2.0)
            .setMin(0.1).setMax(20.0)
            .setSaveConsumer(config::setFixedScreenLineWidthPx)
            .build());

        // [label]
        ConfigCategory label = builder.getOrCreateCategory(Component.literal("Labels"));
        label.addEntry(entryBuilder.startBooleanToggle(
                Component.literal("Show Node Labels"), config.isShowNodeLabels())
            .setDefaultValue(false)
            .setSaveConsumer(config::setShowNodeLabels)
            .build());
        label.addEntry(entryBuilder.startBooleanToggle(
                Component.literal("Show Station Names"), config.isShowStationNames())
            .setDefaultValue(true)
            .setSaveConsumer(config::setShowStationNames)
            .build());

        builder.setSavingRunnable(() -> {
            KineticPlannerMod.LOGGER.info("KP config saved, reloading theme");
            WorldTreeReadOverlay.setTheme(config.toTheme());
        });

        return builder.build();
    }
}
